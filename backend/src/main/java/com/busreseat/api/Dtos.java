package com.busreseat.api;

import com.busreseat.model.Assignment;
import com.busreseat.model.AssignmentStatus;
import com.busreseat.model.Booking;
import com.busreseat.model.CheckIn;
import com.busreseat.model.Seat;
import com.busreseat.model.SeatKind;
import com.busreseat.model.SeatVersion;
import com.busreseat.model.SpecialNeed;
import com.busreseat.model.Trip;
import com.busreseat.model.Vehicle;
import com.busreseat.model.VersionStatus;
import java.time.LocalDateTime;
import java.util.List;

/** REST 传输对象（只出 DTO，不把 JPA 实体/懒加载代理暴露给 Jackson） */
public final class Dtos {

    private Dtos() {
    }

    public record TripSummary(long tripId, String code, String route, String departureTime,
                              String activeVehiclePlate, String activeVehicleModel, int activeVersionNo,
                              long totalBookings, long pendingCount, long manualReviewCount,
                              long splitGroupCount, long checkedInCount) {
    }

    public record BookingView(long bookingId, String name, String phone, String groupId,
                              SpecialNeed need, String originalSeatNo,
                              String seatNo, AssignmentStatus status, boolean groupSplit,
                              String note, boolean checkedIn) {
    }

    public record TripDetail(long tripId, String code, String route, String departureTime,
                             String activeVehiclePlate, String activeVehicleModel, int activeVersionNo,
                             List<BookingView> bookings) {
    }

    public record ReassignResult(long versionId, int versionNo, long vehicleId, String vehiclePlate,
                                 String vehicleModel, List<BookingView> assignments,
                                 List<String> manualReview, List<String> splitGroups,
                                 List<String> warnings) {
    }

    public record SeatView(String seatNo, int rowIdx, int colIdx, SeatKind kind,
                           String occupiedBy, AssignmentStatus status) {
    }

    public record SeatMapView(long versionId, int versionNo, String vehiclePlate, String vehicleModel,
                              List<SeatView> seats) {
    }

    public record ManifestEntry(long bookingId, String name, String seatNo, boolean checkedIn,
                                List<Integer> checkedInVersionNos) {
    }

    public record ManifestView(long tripId, String code, String route, String departureTime,
                               int versionNo, String vehiclePlate, String vehicleModel,
                               List<ManifestEntry> confirmed,
                               List<BookingView> notConfirmed) {
    }

    public record PassengerView(long bookingId, String name, String tripCode, String route,
                                String departureTime, String originalSeatNo,
                                String vehiclePlate, String vehicleModel, int versionNo,
                                String newSeatNo, AssignmentStatus status, String note,
                                boolean groupSplit, boolean checkedIn) {
    }

    public record VehicleOption(long vehicleId, String plateNumber, String model, long totalSeats,
                                long wheelchairSeats, long childSeatSeats, long normalSeats) {
    }

    public record VersionView(long versionId, int versionNo, String vehiclePlate, String vehicleModel,
                              VersionStatus status, String operator, LocalDateTime createdAt) {
    }

    public record SmsView(long id, String phone, String content, LocalDateTime createdAt) {
    }

    public record CheckInView(long bookingId, String name, String seatNo, int versionNo,
                              LocalDateTime checkedAt) {
    }

    public record ErrorView(String message) {
    }

    /* ---------- 组装 ---------- */

    public static BookingView bookingView(Booking b, Assignment a, boolean checkedIn) {
        return new BookingView(b.id, b.passengerName, b.phone, b.groupId, b.need, b.originalSeatNo,
                a == null ? null : a.seatNo,
                a == null ? null : a.status,
                a != null && a.groupSplit,
                a == null ? null : a.note,
                checkedIn);
    }

    public static PassengerView passengerView(Booking b, Assignment a, SeatVersion v, boolean checkedIn) {
        Trip t = b.trip;
        Vehicle veh = v.vehicle;
        return new PassengerView(b.id, b.passengerName, t.code, t.route, t.departureTime,
                b.originalSeatNo, veh.plateNumber, veh.model, v.versionNo,
                a == null ? null : a.seatNo,
                a == null ? null : a.status,
                a == null ? null : a.note,
                a != null && a.groupSplit,
                checkedIn);
    }

    public static VehicleOption vehicleOption(Vehicle v, List<Seat> seats) {
        long wheel = seats.stream().filter(s -> s.kind == SeatKind.WHEELCHAIR).count();
        long child = seats.stream().filter(s -> s.kind == SeatKind.CHILD_SEAT).count();
        return new VehicleOption(v.id, v.plateNumber, v.model, seats.size(), wheel, child,
                seats.size() - wheel - child);
    }
}
