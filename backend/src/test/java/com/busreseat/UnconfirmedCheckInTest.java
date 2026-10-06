package com.busreseat;

import static io.restassured.RestAssured.given;

import com.busreseat.model.SpecialNeed;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 场景三：乘客未确认新座位前不能检票登车；转人工的乘客不能自行确认 */
@QuarkusTest
class UnconfirmedCheckInTest {

    @Inject
    TestFixtures fixtures;

    @Test
    void unconfirmedPassengerCannotCheckIn() {
        long busA = fixtures.createBus("测A3001", 12, Set.of(), Set.of());
        // 备用车 1：5A 是轮椅位 → 普通乘客保不住原 5A，变成待确认
        long busB = fixtures.createBus("测B3001", 10, Set.of("5A"), Set.of());
        // 备用车 2：没有轮椅位 → 轮椅乘客转人工
        long busC = fixtures.createBus("测C3001", 9, Set.of(), Set.of());
        long tripId = fixtures.createTrip("TC-CHECKIN", busA, List.of(
                new TestFixtures.BookingSpec("普通客", null, SpecialNeed.NONE, "5A"),
                new TestFixtures.BookingSpec("轮椅客", null, SpecialNeed.WHEELCHAIR, "1A")
        ));
        long normal = fixtures.bookingIdOf("TC-CHECKIN", "普通客");
        long wheel = fixtures.bookingIdOf("TC-CHECKIN", "轮椅客");

        // 换到备用车 1：普通客被挪走（PENDING），轮椅客分到 5A 轮椅位
        given().contentType(ContentType.JSON).body(Map.of("vehicleId", busB, "operator", "调度"))
                .post("/api/dispatcher/trips/{id}/change-vehicle", tripId)
                .then().statusCode(200);

        // 未确认 → 检票 409
        given().contentType(ContentType.JSON).body(Map.of("bookingId", normal))
                .post("/api/checkins")
                .then().statusCode(409);

        // 确认后 → 检票 200
        given().post("/api/passenger/bookings/{id}/confirm", normal)
                .then().statusCode(200)
                .body("status", org.hamcrest.Matchers.is("CONFIRMED"));
        given().contentType(ContentType.JSON).body(Map.of("bookingId", normal))
                .post("/api/checkins")
                .then().statusCode(200);

        // 再换到无轮椅位的车：轮椅客转人工，不能自行确认、不能检票
        given().contentType(ContentType.JSON).body(Map.of("vehicleId", busC, "operator", "调度"))
                .post("/api/dispatcher/trips/{id}/change-vehicle", tripId)
                .then().statusCode(200)
                .body("manualReview", org.hamcrest.Matchers.hasItem("轮椅客"));

        given().post("/api/passenger/bookings/{id}/confirm", wheel)
                .then().statusCode(409);
        given().contentType(ContentType.JSON).body(Map.of("bookingId", wheel))
                .post("/api/checkins")
                .then().statusCode(409);

        // 司机最终名单里只有已确认的乘客
        given().get("/api/driver/trips/{id}/manifest", tripId)
                .then().statusCode(200)
                .body("confirmed.name", org.hamcrest.Matchers.hasItem("普通客"))
                .body("confirmed.name", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("轮椅客")))
                .body("notConfirmed.name", org.hamcrest.Matchers.hasItem("轮椅客"));
    }
}
