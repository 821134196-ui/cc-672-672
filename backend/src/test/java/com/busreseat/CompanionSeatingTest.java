package com.busreseat;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.busreseat.model.SpecialNeed;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 场景一：换车后同行人应被安排到同排相邻座位，轮椅/儿童座椅需求优先满足 */
@QuarkusTest
class CompanionSeatingTest {

    @Inject
    TestFixtures fixtures;

    @Test
    void companionsAdjacentAndSpecialNeedsSatisfied() {
        long oldBus = fixtures.createBus("测A1001", 12, Set.of(), Set.of());
        long newBus = fixtures.createBus("测B1001", 10, Set.of("1A", "1B"), Set.of("2A", "2B"));
        long tripId = fixtures.createTrip("TC-COMPANION", oldBus, List.of(
                new TestFixtures.BookingSpec("甲", "G1", SpecialNeed.NONE, "5A"),
                new TestFixtures.BookingSpec("乙", "G1", SpecialNeed.NONE, "5B"),
                new TestFixtures.BookingSpec("丙", "G1", SpecialNeed.CHILD_SEAT, "5C"),
                new TestFixtures.BookingSpec("丁一", "G2", SpecialNeed.NONE, "9A"),
                new TestFixtures.BookingSpec("丁二", "G2", SpecialNeed.NONE, "9B"),
                new TestFixtures.BookingSpec("丁三", "G2", SpecialNeed.NONE, "9C"),
                new TestFixtures.BookingSpec("丁四", "G2", SpecialNeed.NONE, "9D"),
                new TestFixtures.BookingSpec("轮椅客", null, SpecialNeed.WHEELCHAIR, "1A"),
                new TestFixtures.BookingSpec("单人", null, SpecialNeed.NONE, "3A")
        ));

        JsonPath result = given()
                .contentType(ContentType.JSON)
                .body(Map.of("vehicleId", newBus, "operator", "测试调度"))
                .when()
                .post("/api/dispatcher/trips/{id}/change-vehicle", tripId)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        Map<String, String> seatOf = result.getMap("assignments.collectEntries { [(it.name): it.seatNo] }");
        Map<String, String> statusOf = result.getMap("assignments.collectEntries { [(it.name): it.status] }");

        // 轮椅需求 → 轮椅位；儿童座椅需求 → 儿童座椅位
        assertTrue(Set.of("1A", "1B").contains(seatOf.get("轮椅客")), "轮椅乘客应分到轮椅位");
        assertTrue(Set.of("2A", "2B").contains(seatOf.get("丙")), "儿童座椅需求应分到儿童座椅位");

        // 四人同行组：同一排且列号连续
        List<String> g2 = List.of(seatOf.get("丁一"), seatOf.get("丁二"), seatOf.get("丁三"), seatOf.get("丁四"));
        assertAdjacent(g2, "四人同行组应同排相邻");

        // 三人组（含儿童座椅成员）：普通成员应与儿童座椅位同排（第 2 排）
        assertEquals(rowOf(seatOf.get("丙")), rowOf(seatOf.get("甲")), "同行人应与特殊需求成员同排");
        assertEquals(rowOf(seatOf.get("丙")), rowOf(seatOf.get("乙")), "同行人应与特殊需求成员同排");

        // 没有转人工、没有拆散
        assertEquals(List.of(), result.getList("manualReview"));
        assertEquals(List.of(), result.getList("splitGroups"));

        // 同一座位不能分给两个人
        Set<String> allSeats = new HashSet<>(seatOf.values());
        assertEquals(seatOf.size(), allSeats.size(), "座位不能重复分配");

        // 座位号变化的需要乘客确认
        assertEquals("PENDING", statusOf.get("丁一"));
    }

    static void assertAdjacent(List<String> seats, String message) {
        Set<Integer> rows = new HashSet<>();
        List<Integer> cols = seats.stream().map(CompanionSeatingTest::colOf).sorted().toList();
        seats.forEach(s -> rows.add(rowOf(s)));
        assertEquals(1, rows.size(), message + "（应在同一排）: " + seats);
        for (int i = 1; i < cols.size(); i++) {
            assertEquals(cols.get(i - 1) + 1, cols.get(i).intValue(), message + "（列应连续）: " + seats);
        }
    }

    static int rowOf(String seatNo) {
        return Integer.parseInt(seatNo.replaceAll("[A-Z]", ""));
    }

    static int colOf(String seatNo) {
        return seatNo.replaceAll("[0-9]", "").charAt(0) - 'A' + 1;
    }
}
