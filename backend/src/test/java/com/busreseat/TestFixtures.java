package com.busreseat;

import com.busreseat.model.Assignment;
import com.busreseat.model.AssignmentStatus;
import com.busreseat.model.Booking;
import com.busreseat.model.Seat;
import com.busreseat.model.SeatKind;
import com.busreseat.model.SeatVersion;
import com.busreseat.model.SpecialNeed;
import com.busreseat.model.Trip;
import com.busreseat.model.Vehicle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;

/** 测试夹具：每个测试用例自建车辆/班次/订单，互不干扰 */
@ApplicationScoped
public class TestFixtures {

    public record BookingSpec(String name, String groupId, SpecialNeed need, String seatNo) {
    }

    @Transactional
    public long createBus(String plate, int rows, Set<String> wheelchairSeats, Set<String> childSeats) {
        Vehicle v = new Vehicle();
        v.plateNumber = plate;
        v.model = "测试车型-" + plate;
        v.persist();
        String[] cols = {"A", "B", "C", "D"};
        for (int r = 1; r <= rows; r++) {
            for (int c = 0; c < cols.length; c++) {
                Seat s = new Seat();
                s.vehicle = v;
                s.seatNo = r + cols[c];
                s.rowIdx = r;
                s.colIdx = c + 1;
                if (wheelchairSeats.contains(s.seatNo)) {
                    s.kind = SeatKind.WHEELCHAIR;
                } else if (childSeats.contains(s.seatNo)) {
                    s.kind = SeatKind.CHILD_SEAT;
                }
                s.persist();
            }
        }
        return v.id;
    }

    /** 建班次 + 初始版本 v1（全部已确认）+ 订单，返回班次 id */
    @Transactional
    public long createTrip(String code, long vehicleId, List<BookingSpec> specs) {
        Vehicle v = Vehicle.findById(vehicleId);
        Trip trip = new Trip();
        trip.code = code;
        trip.route = "测试线路";
        trip.departureTime = "2026-10-08 09:00";
        trip.baseVehicle = v;
        trip.activeVehicle = v;
        trip.persist();

        SeatVersion v1 = new SeatVersion();
        v1.trip = trip;
        v1.vehicle = v;
        v1.versionNo = 1;
        v1.operator = "测试初始";
        v1.persist();
        trip.activeVersion = v1;

        for (BookingSpec spec : specs) {
            Booking b = new Booking();
            b.trip = trip;
            b.passengerName = spec.name();
            b.phone = "13900000000";
            b.groupId = spec.groupId();
            b.need = spec.need();
            b.originalSeatNo = spec.seatNo();
            b.persist();

            Assignment a = new Assignment();
            a.version = v1;
            a.booking = b;
            a.seatNo = spec.seatNo();
            a.status = AssignmentStatus.CONFIRMED;
            a.persist();
        }
        return trip.id;
    }

    @Transactional
    public long bookingIdOf(String tripCode, String name) {
        Booking b = Booking.find("trip.code = ?1 and passengerName = ?2", tripCode, name).firstResult();
        return b.id;
    }

    @Transactional
    public long activeVersionIdOf(String tripCode) {
        Trip t = Trip.find("code", tripCode).firstResult();
        return t.activeVersion.id;
    }
}
