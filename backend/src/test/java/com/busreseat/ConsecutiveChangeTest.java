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
import org.junit.jupiter.api.Test;

/** 场景二：连续两次换车，旧座位版本与此前检票记录都要保留 */
@QuarkusTest
class ConsecutiveChangeTest {

    @Inject
    TestFixtures fixtures;

    @Test
    void consecutiveChangesKeepVersionsAndCheckIns() {
        long busA = fixtures.createBus("测A2001", 12, Set.of(), Set.of());
        long busB = fixtures.createBus("测B2001", 10, Set.of(), Set.of());
        long busC = fixtures.createBus("测C2001", 9, Set.of(), Set.of());
        long tripId = fixtures.createTrip("TC-CONSEC", busA, List.of(
                new TestFixtures.BookingSpec("乘客甲", null, SpecialNeed.NONE, "6B"),
                new TestFixtures.BookingSpec("乘客乙", null, SpecialNeed.NONE, "3A")
        ));
        long jia = fixtures.bookingIdOf("TC-CONSEC", "乘客甲");

        // 第一次换车：A → B（座位号不变，确认状态继承）
        given().contentType(ContentType.JSON).body(Map.of("vehicleId", busB, "operator", "调度1"))
                .post("/api/dispatcher/trips/{id}/change-vehicle", tripId)
                .then().statusCode(200)
                .body("versionNo", org.hamcrest.Matchers.is(2));

        // 乘客甲座位未变（6B 继承已确认），可以检票
        given().contentType(ContentType.JSON).body(Map.of("bookingId", jia))
                .post("/api/checkins")
                .then().statusCode(200)
                .body("versionNo", org.hamcrest.Matchers.is(2));

        // 第二次换车：B → C
        given().contentType(ContentType.JSON).body(Map.of("vehicleId", busC, "operator", "调度2"))
                .post("/api/dispatcher/trips/{id}/change-vehicle", tripId)
                .then().statusCode(200)
                .body("versionNo", org.hamcrest.Matchers.is(3));

        // 三个座位版本都保留，只有 v3 生效
        JsonPath versions = given()
                .get("/api/dispatcher/trips/{id}/versions", tripId)
                .then().statusCode(200).extract().jsonPath();
        assertEquals(3, versions.getList("").size());
        assertEquals(List.of("SUPERSEDED", "SUPERSEDED", "ACTIVE"), versions.getList("status"));

        // 此前（v2）的检票记录保留
        JsonPath checkIns = given()
                .get("/api/checkins/trip/{id}", tripId)
                .then().statusCode(200).extract().jsonPath();
        assertEquals(1, checkIns.getList("").size());
        assertEquals(2, (int) checkIns.getInt("[0].versionNo"), "v2 的检票记录应保留");

        // 司机名单仍显示乘客甲已检票（记录来自历史版本）
        JsonPath manifest = given()
                .get("/api/driver/trips/{id}/manifest", tripId)
                .then().statusCode(200).extract().jsonPath();
        Map<String, Boolean> checkedInOf = manifest.getMap("confirmed.collectEntries { [(it.name): it.checkedIn] }");
        assertTrue(checkedInOf.get("乘客甲"), "检票记录跨换车保留");
    }
}
