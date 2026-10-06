package com.example.bus;

import com.example.bus.service.DataSeeder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试（真实 SQLite + 真实 HTTP + 真实事务）：
 * 1. 连续换车保留检票记录与座位版本；
 * 2. 未确认新座位的乘客禁止检票；
 * 3. 两名调度并发换车不会产生两个活动版本、不会一座两人。
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class SeatFlowTest {

    static final String BASE = "http://localhost:8091";
    HttpClient http = HttpClient.newHttpClient();

    @Inject
    DataSeeder seeder;

    @BeforeEach
    void resetData() {
        seeder.reset();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Object json(HttpResponse<String> r) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(r.body(), Object.class);
    }

    @Test
    void 连续换车_检票记录与版本历史完整保留() throws Exception {
        // v1：种子数据全员已确认，张伟(id=1) 检票
        assertEquals(200, post("/api/boarding/1/board", "{\"operator\":\"司机\"}").statusCode());

        // 换车 -> v2，所有分配回到 PROPOSED
        var r2 = post("/api/seat/swap",
                "{\"vehicleCode\":\"B-45\",\"expectedVersionNo\":1,\"operator\":\"调度\"}");
        assertEquals(200, r2.statusCode());
        Map<String, Object> m2 = (Map<String, Object>) json(r2);
        assertEquals(2, ((Map<?, ?>) m2.get("version")).get("versionNo"));

        // v2 未确认时检票必须被拒绝
        var reject = post("/api/boarding/1/board", "{}");
        assertEquals(409, reject.statusCode());
        assertTrue(reject.body().contains("尚未确认"));

        // 张伟确认 v2 新座后检票成功
        assertEquals(200, post("/api/passengers/1/confirm", "{\"expectedVersionNo\":2}").statusCode());
        var board2 = post("/api/boarding/1/board", "{\"operator\":\"司机\"}");
        assertEquals(200, board2.statusCode());

        // 再次换车 -> v3（E-15，含拆散/转人工问题）
        var r3 = post("/api/seat/swap",
                "{\"vehicleCode\":\"E-15\",\"expectedVersionNo\":2,\"operator\":\"调度\"}");
        assertEquals(200, r3.statusCode());

        // 版本链完整：v1/v2 历史保留，v2 中张伟仍显示已检票
        var versions = (List<Map<String, Object>>) json(get("/api/versions"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vlist = (List<Map<String, Object>>) versions;
        assertEquals(3, vlist.size());

        var v2detail = (Map<String, Object>) json(get("/api/versions/2"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) v2detail.get("assignments");
        Map<String, Object> zhang = rows.stream()
                .filter(a -> "张伟".equals(a.get("name"))).findFirst().orElseThrow();
        assertEquals(true, zhang.get("boarded"), "历史版本 v2 的检票记录必须保留");
        assertEquals("CONFIRMED", zhang.get("status"));

        // v3 为当前活动版本，检票计数归零（乘客需在新车上重新确认）
        var v3detail = (Map<String, Object>) json(get("/api/manifest"));
        Map<?, ?> stats = (Map<?, ?>) v3detail.get("stats");
        assertEquals(3, ((Map<?, ?>) v3detail.get("version")).get("versionNo"));
        assertEquals(0, ((Number) stats.get("boarded")).intValue());
    }

    @Test
    void 并发换车_只有一个活动版本且座位不重复() throws Exception {
        // 两名调度同时基于 v1 提交不同新车（CountDownLatch 让两请求尽量同时发出）
        List<HttpResponse<String>> results = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (String car : List.of("B-45", "E-15")) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                    results.add(post("/api/seat/swap",
                            "{\"vehicleCode\":\"" + car + "\",\"expectedVersionNo\":1}"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        ready.await();
        go.countDown();
        for (Thread t : threads) t.join(15000);

        long ok = results.stream().filter(r -> r.statusCode() == 200).count();
        long conflict = results.stream().filter(r -> r.statusCode() == 409).count();
        assertEquals(1, ok, "两次并发换车必须恰好一次成功");
        assertEquals(1, conflict, "另一次必须收到 409 版本冲突");

        // 只有一个 ACTIVE 版本
        var versions = (List<Map<String, Object>>) json(get("/api/versions"));
        @SuppressWarnings("unchecked")
        long active = ((List<Map<String, Object>>) versions).stream()
                .filter(v -> "ACTIVE".equals(v.get("status"))).count();
        assertEquals(1, active);

        // 当前名单一座一人
        var manifest = (Map<String, Object>) json(get("/api/manifest"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) manifest.get("assignments");
        var seats = rows.stream().map(a -> (String) a.get("newSeat")).filter(s -> !s.isEmpty()).toList();
        assertEquals(seats.size(), new java.util.HashSet<>(seats).size(), "同一座位不能分给两名乘客");
    }
}
