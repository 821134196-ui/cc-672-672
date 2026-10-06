package com.busreseat.service;

import com.busreseat.api.Dtos;
import com.busreseat.model.Assignment;
import com.busreseat.model.AssignmentStatus;
import com.busreseat.model.Booking;
import com.busreseat.model.CheckIn;
import com.busreseat.model.Seat;
import com.busreseat.model.SeatVersion;
import com.busreseat.model.Trip;
import com.busreseat.model.VersionStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 班次查询、乘客确认、检票、司机名单 */
@ApplicationScoped
public class TripService {

    /* ---------- 调度端查询 ---------- */

    @Transactional
    public List<Dtos.TripSummary> listTrips() {
        return Trip.<Trip>listAll().stream().map(this::summary).collect(Collectors.toList());
    }

    @Transactional
    public Dtos.TripDetail tripDetail(long tripId) {
        Trip trip = mustTrip(tripId);
        SeatVersion v = trip.activeVersion;
        Map<Long, Assignment> byBooking = assignmentsByBooking(v);
        List<Dtos.BookingView> bookings = Booking.<Booking>list("trip.id = ?1 order by id", tripId)
                .stream()
                .map(b -> Dtos.bookingView(b, byBooking.get(b.id), hasCheckIn(b.id)))
                .collect(Collectors.toList());
        return new Dtos.TripDetail(trip.id, trip.code, trip.route, trip.departureTime,
                trip.activeVehicle.plateNumber, trip.activeVehicle.model,
                v == null ? 0 : v.versionNo, bookings);
    }

    @Transactional
    public List<Dtos.VersionView> versions(long tripId) {
        mustTrip(tripId);
        return SeatVersion.<SeatVersion>list("trip.id = ?1 order by versionNo", tripId).stream()
                .map(v -> new Dtos.VersionView(v.id, v.versionNo, v.vehicle.plateNumber, v.vehicle.model,
                        v.status, v.operator, v.createdAt))
                .collect(Collectors.toList());
    }

    @Transactional
    public Dtos.SeatMapView seatMap(long versionId) {
        SeatVersion v = mustVersion(versionId);
        Map<String, Assignment> bySeatNo = Assignment.<Assignment>list("version.id", versionId).stream()
                .filter(a -> a.seatNo != null)
                .collect(Collectors.toMap(a -> a.seatNo, Function.identity()));
        List<Dtos.SeatView> seats = Seat.<Seat>list("vehicle.id = ?1 order by rowIdx, colIdx", v.vehicle.id)
                .stream()
                .map(s -> {
                    Assignment a = bySeatNo.get(s.seatNo);
                    return new Dtos.SeatView(s.seatNo, s.rowIdx, s.colIdx, s.kind,
                            a == null ? null : a.booking.passengerName,
                            a == null ? null : a.status);
                })
                .collect(Collectors.toList());
        return new Dtos.SeatMapView(v.id, v.versionNo, v.vehicle.plateNumber, v.vehicle.model, seats);
    }

    /* ---------- 乘客端 ---------- */

    @Transactional
    public Dtos.PassengerView passengerView(long bookingId) {
        Booking b = mustBooking(bookingId);
        SeatVersion v = b.trip.activeVersion;
        Assignment a = v == null ? null
                : Assignment.find("version.id = ?1 and booking.id = ?2", v.id, bookingId).firstResult();
        return Dtos.passengerView(b, a, v, hasCheckIn(bookingId));
    }

    /** 乘客确认新座位；转人工的不能自行确认 */
    @Transactional
    public Dtos.PassengerView confirm(long bookingId) {
        Booking b = mustBooking(bookingId);
        SeatVersion v = b.trip.activeVersion;
        Assignment a = v == null ? null
                : Assignment.find("version.id = ?1 and booking.id = ?2", v.id, bookingId).firstResult();
        if (a == null) {
            throw new ClientErrorException("当前版本没有该乘客的座位分配", 409);
        }
        if (a.status == AssignmentStatus.MANUAL_REVIEW) {
            throw new ClientErrorException("特殊需求未满足，已转人工处理，暂不能确认", 409);
        }
        a.status = AssignmentStatus.CONFIRMED;
        return Dtos.passengerView(b, a, v, hasCheckIn(bookingId));
    }

    /* ---------- 检票 ---------- */

    /** 乘客未确认当前版本新座位前，不能记为已登车 */
    @Transactional
    public Dtos.CheckInView checkIn(long bookingId) {
        Booking b = mustBooking(bookingId);
        SeatVersion v = b.trip.activeVersion;
        if (v == null) {
            throw new ClientErrorException("班次还没有座位版本", 409);
        }
        Assignment a = Assignment.find("version.id = ?1 and booking.id = ?2", v.id, bookingId).firstResult();
        if (a == null || a.status != AssignmentStatus.CONFIRMED) {
            throw new ClientErrorException("乘客尚未确认新座位，不能检票登车", 409);
        }
        CheckIn existing = CheckIn.find("booking.id = ?1 and version.id = ?2", bookingId, v.id).firstResult();
        if (existing != null) {
            return checkInView(existing);
        }
        CheckIn c = new CheckIn();
        c.booking = b;
        c.version = v;
        c.persist();
        return checkInView(c);
    }

    @Transactional
    public List<Dtos.CheckInView> checkInsOfTrip(long tripId) {
        mustTrip(tripId);
        return CheckIn.<CheckIn>list("booking.trip.id = ?1 order by id", tripId).stream()
                .map(this::checkInView)
                .collect(Collectors.toList());
    }

    /* ---------- 司机名单：只给当前生效版本里乘客已确认的最终名单 ---------- */

    @Transactional
    public Dtos.ManifestView manifest(long tripId) {
        Trip trip = mustTrip(tripId);
        SeatVersion v = trip.activeVersion;
        List<Assignment> assignments = v == null ? List.of()
                : Assignment.list("version.id", v.id);
        List<Dtos.ManifestEntry> confirmed = assignments.stream()
                .filter(a -> a.status == AssignmentStatus.CONFIRMED)
                .map(a -> new Dtos.ManifestEntry(a.booking.id, a.booking.passengerName, a.seatNo,
                        hasCheckIn(a.booking.id), checkInVersionNos(a.booking.id)))
                .collect(Collectors.toList());
        List<Dtos.BookingView> notConfirmed = assignments.stream()
                .filter(a -> a.status != AssignmentStatus.CONFIRMED)
                .map(a -> Dtos.bookingView(a.booking, a, hasCheckIn(a.booking.id)))
                .collect(Collectors.toList());
        return new Dtos.ManifestView(trip.id, trip.code, trip.route, trip.departureTime,
                v == null ? 0 : v.versionNo,
                trip.activeVehicle.plateNumber, trip.activeVehicle.model,
                confirmed, notConfirmed);
    }

    /* ---------- 内部 ---------- */

    private Dtos.TripSummary summary(Trip trip) {
        SeatVersion v = trip.activeVersion;
        List<Assignment> assignments = v == null ? List.of() : Assignment.list("version.id", v.id);
        long pending = assignments.stream().filter(a -> a.status == AssignmentStatus.PENDING).count();
        long manual = assignments.stream().filter(a -> a.status == AssignmentStatus.MANUAL_REVIEW).count();
        long split = assignments.stream().filter(a -> a.groupSplit).count();
        long checkedIn = CheckIn.count("booking.trip.id", trip.id);
        return new Dtos.TripSummary(trip.id, trip.code, trip.route, trip.departureTime,
                trip.activeVehicle.plateNumber, trip.activeVehicle.model,
                v == null ? 0 : v.versionNo,
                Booking.count("trip.id", trip.id), pending, manual, split, checkedIn);
    }

    private Map<Long, Assignment> assignmentsByBooking(SeatVersion v) {
        if (v == null) {
            return Map.of();
        }
        return Assignment.<Assignment>list("version.id", v.id).stream()
                .collect(Collectors.toMap(a -> a.booking.id, Function.identity()));
    }

    private Dtos.CheckInView checkInView(CheckIn c) {
        Assignment a = Assignment.find("version.id = ?1 and booking.id = ?2", c.version.id, c.booking.id)
                .firstResult();
        return new Dtos.CheckInView(c.booking.id, c.booking.passengerName,
                a == null ? null : a.seatNo, c.version.versionNo, c.checkedAt);
    }

    private static boolean hasCheckIn(long bookingId) {
        return CheckIn.count("booking.id", bookingId) > 0;
    }

    private static List<Integer> checkInVersionNos(long bookingId) {
        return CheckIn.<CheckIn>list("booking.id", bookingId).stream()
                .map(c -> c.version.versionNo)
                .collect(Collectors.toList());
    }

    private static Trip mustTrip(long tripId) {
        Trip trip = Trip.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("班次不存在: " + tripId);
        }
        return trip;
    }

    private static Booking mustBooking(long bookingId) {
        Booking b = Booking.findById(bookingId);
        if (b == null) {
            throw new NotFoundException("订单不存在: " + bookingId);
        }
        return b;
    }

    private static SeatVersion mustVersion(long versionId) {
        SeatVersion v = SeatVersion.findById(versionId);
        if (v == null) {
            throw new NotFoundException("座位版本不存在: " + versionId);
        }
        return v;
    }
}
