package com.busreseat;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.busreseat.model.SpecialNeed;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** 场景四：两名调度同时操作，不能把同一个座位分给两个人 */
@QuarkusTest
class ConcurrentAssignmentTest {

    @Inject
    TestFixtures fixtures;

    /** 并发手动调同一座位：唯一约束保证只有一个成功 */
    @Test
    void concurrentManualAssignSameSeatOnlyOneWins() throws Exception {
        long bus = fixtures.createBus("测A4001", 12, Set.of(), Set.of());
        long tripId = fixtures.createTrip("TC-CONC-SEAT", bus, List.of(
                new TestFixtures.BookingSpec("乘客一", null, SpecialNeed.NONE, "1A"),
                new TestFixtures.BookingSpec("乘客二", null, SpecialNeed.NONE, "1B")
        ));
        long versionId = fixtures.activeVersionIdOf("TC-CONC-SEAT");
        long p1 = fixtures.bookingIdOf("TC-CONC-SEAT", "乘客一");
        long p2 = fixtures.bookingIdOf("TC-CONC-SEAT", "乘客二");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> assignP1 = () -> assign(versionId, p1, "5A", ready, go);
        Callable<Integer> assignP2 = () -> assign(versionId, p2, "5A", ready, go);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Integer> f1 = pool.submit(assignP1);
        Future<Integer> f2 = pool.submit(assignP2);
        go.countDown();
        int s1 = f1.get(30, TimeUnit.SECONDS);
        int s2 = f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(Set.of(200, 409), Set.of(s1, s2),
                "同一座位并发分配应一个成功一个冲突，实际: " + s1 + "," + s2);

        // 最终 5A 只被一个人占用
        JsonPath seatMap = given().get("/api/dispatcher/versions/{id}/seats", versionId)
                .then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> seats = seatMap.getList("seats");
        long occupied = seats.stream()
                .filter(s -> "5A".equals(s.get("seatNo")) && s.get("occupiedBy") != null)
                .count();
        assertEquals(1, occupied, "5A 只能属于一名乘客");
    }

    /** 并发换车：乐观锁保证只有一个调度的整车重排生效 */
    @Test
    void concurrentChangeVehicleOnlyOneWins() throws Exception {
        long busA = fixtures.createBus("测A4002", 12, Set.of(), Set.of());
        long busB = fixtures.createBus("测B4002", 10, Set.of(), Set.of());
        long busC = fixtures.createBus("测C4002", 9, Set.of(), Set.of());
        long tripId = fixtures.createTrip("TC-CONC-CHANGE", busA, List.of(
                new TestFixtures.BookingSpec("乘客甲", null, SpecialNeed.NONE, "1A"),
                new TestFixtures.BookingSpec("乘客乙", null, SpecialNeed.NONE, "1B")
        ));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> toB = () -> changeVehicle(tripId, busB, ready, go);
        Callable<Integer> toC = () -> changeVehicle(tripId, busC, ready, go);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Integer> f1 = pool.submit(toB);
        Future<Integer> f2 = pool.submit(toC);
        go.countDown();
        int s1 = f1.get(30, TimeUnit.SECONDS);
        int s2 = f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(Set.of(200, 409), Set.of(s1, s2),
                "并发换车应一个成功一个冲突，实际: " + s1 + "," + s2);

        // 只产生一个新版本（v2），没有半个重排结果
        JsonPath versions = given().get("/api/dispatcher/trips/{id}/versions", tripId)
                .then().statusCode(200).extract().jsonPath();
        assertEquals(2, versions.getList("").size());
        assertEquals(List.of("SUPERSEDED", "ACTIVE"), versions.getList("status"));
    }

    private static int assign(long versionId, long bookingId, String seatNo,
                              CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        go.await(10, TimeUnit.SECONDS);
        return given().contentType(ContentType.JSON)
                .body(Map.of("bookingId", bookingId, "seatNo", seatNo))
                .post("/api/dispatcher/versions/{id}/assign", versionId)
                .statusCode();
    }

    private static int changeVehicle(long tripId, long vehicleId,
                                     CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        go.await(10, TimeUnit.SECONDS);
        return given().contentType(ContentType.JSON)
                .body(Map.of("vehicleId", vehicleId, "operator", "并发调度"))
                .post("/api/dispatcher/trips/{id}/change-vehicle", tripId)
                .statusCode();
    }
}
