package com.busreseat.service;

import com.busreseat.api.Dtos;
import com.busreseat.model.Assignment;
import com.busreseat.model.AssignmentStatus;
import com.busreseat.model.Booking;
import com.busreseat.model.Seat;
import com.busreseat.model.SeatKind;
import com.busreseat.model.SeatVersion;
import com.busreseat.model.SpecialNeed;
import com.busreseat.model.Trip;
import com.busreseat.model.Vehicle;
import com.busreseat.model.VersionStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 换车座位重排核心逻辑。
 * 规则：特殊需求（轮椅/儿童座椅）优先 → 同行人整组相邻 → 单人尽量保留原座位号。
 * 无法满足的需求转人工；同行人被拆散时打预警标记。
 */
@ApplicationScoped
public class ReassignmentService {

    @Inject
    SmsService smsService;

    @Transactional
    public Dtos.ReassignResult changeVehicle(long tripId, long newVehicleId, String operator) {
        Trip trip = Trip.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("班次不存在: " + tripId);
        }
        Vehicle vehicle = Vehicle.findById(newVehicleId);
        if (vehicle == null) {
            throw new NotFoundException("车辆不存在: " + newVehicleId);
        }
        if (trip.activeVehicle.id.equals(vehicle.id)) {
            throw new ClientErrorException("新车与当前使用车辆相同", 409);
        }

        SeatVersion oldVersion = trip.activeVersion;
        Map<Long, Assignment> prevByBooking = new HashMap<>();
        if (oldVersion != null) {
            for (Assignment a : Assignment.<Assignment>list("version.id", oldVersion.id)) {
                prevByBooking.put(a.booking.id, a);
            }
            oldVersion.status = VersionStatus.SUPERSEDED;
        }

        SeatVersion version = new SeatVersion();
        version.trip = trip;
        version.vehicle = vehicle;
        version.versionNo = oldVersion == null ? 1 : oldVersion.versionNo + 1;
        version.operator = operator;
        version.persist();

        List<Seat> seats = Seat.list("vehicle.id = ?1 order by rowIdx, colIdx", vehicle.id);
        Placer placer = new Placer(seats);
        List<Booking> bookings = Booking.list("trip.id = ?1 order by id", trip.id);

        Map<Long, Assignment> freshByBooking = new HashMap<>();
        List<String> manualReview = new ArrayList<>();
        Set<String> splitGroups = new LinkedHashSet<>();

        // 1) 轮椅需求优先占用轮椅位
        for (Booking b : bookings) {
            if (b.need == SpecialNeed.WHEELCHAIR) {
                Seat s = placer.takeKind(SeatKind.WHEELCHAIR);
                if (s == null) {
                    toManualReview(b, version, "新车没有可用轮椅位，已转人工处理", freshByBooking, manualReview);
                } else {
                    place(b, s, version, prevByBooking, freshByBooking);
                }
            }
        }
        // 2) 儿童座椅需求
        for (Booking b : bookings) {
            if (b.need == SpecialNeed.CHILD_SEAT) {
                Seat s = placer.takeKind(SeatKind.CHILD_SEAT);
                if (s == null) {
                    toManualReview(b, version, "新车没有可安装儿童座椅的座位，已转人工处理", freshByBooking, manualReview);
                } else {
                    place(b, s, version, prevByBooking, freshByBooking);
                }
            }
        }
        // 3) 同行人分组：整组安排同排相邻座位；已因特殊需求落座的成员所在排优先
        Map<String, List<Booking>> groups = bookings.stream()
                .filter(b -> b.groupId != null && !freshByBooking.containsKey(b.id))
                .collect(Collectors.groupingBy(b -> b.groupId, LinkedHashMap::new, Collectors.toList()));
        List<Map.Entry<String, List<Booking>>> sortedGroups = new ArrayList<>(groups.entrySet());
        sortedGroups.sort((g1, g2) -> Integer.compare(g2.getValue().size(), g1.getValue().size()));

        for (Map.Entry<String, List<Booking>> entry : sortedGroups) {
            String groupId = entry.getKey();
            List<Booking> members = entry.getValue();
            // 同组已落座成员（特殊需求）所在的排，作为锚点；转人工（无座位）的成员不算锚点
            Set<Integer> anchorRows = bookings.stream()
                    .filter(b -> groupId.equals(b.groupId) && freshByBooking.containsKey(b.id))
                    .map(b -> freshByBooking.get(b.id).seatNo)
                    .filter(seatNo -> seatNo != null)
                    .map(seatNo -> seatRowOf(seatNo, seats))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            List<Seat> run = placer.takeAdjacentRun(members.size(), anchorRows);
            if (run != null) {
                for (int i = 0; i < members.size(); i++) {
                    place(members.get(i), run.get(i), version, prevByBooking, freshByBooking);
                }
            } else {
                // 排不下整组：分散安排并打拆散预警
                for (Booking member : members) {
                    Seat s = placer.takeFirstNormal();
                    if (s == null) {
                        toManualReview(member, version, "新车座位不足，已转人工处理", freshByBooking, manualReview);
                    } else {
                        Assignment a = place(member, s, version, prevByBooking, freshByBooking);
                        a.groupSplit = true;
                        a.note = "同行人 " + groupId + " 无法安排相邻座位，已被拆散";
                    }
                }
                splitGroups.add(groupId);
            }
        }
        // 4) 单人：尽量保留原座位号，否则取首个空闲普通座
        for (Booking b : bookings) {
            if (freshByBooking.containsKey(b.id)) {
                continue;
            }
            Assignment prev = prevByBooking.get(b.id);
            Seat s = null;
            if (prev != null && prev.seatNo != null) {
                s = placer.takeSeatNoIfNormal(prev.seatNo);
            }
            if (s == null) {
                s = placer.takeFirstNormal();
            }
            if (s == null) {
                toManualReview(b, version, "新车座位不足，已转人工处理", freshByBooking, manualReview);
            } else {
                place(b, s, version, prevByBooking, freshByBooking);
            }
        }

        trip.activeVehicle = vehicle;
        trip.activeVersion = version;
        try {
            Assignment.flush();
        } catch (PersistenceException e) {
            throw new ClientErrorException("座位分配冲突：同一座位被同时分配，请重试", 409);
        }

        // 短信通知（本地模拟）
        for (Booking b : bookings) {
            Assignment a = freshByBooking.get(b.id);
            smsService.notifyReassigned(b, a, trip, vehicle);
        }

        List<String> warnings = new ArrayList<>();
        for (String g : splitGroups) {
            warnings.add("同行人分组 " + g + " 被拆散，请调度关注");
        }
        for (String m : manualReview) {
            warnings.add(m + " 的特殊需求无法满足，已转人工处理");
        }

        List<Dtos.BookingView> views = bookings.stream()
                .map(b -> Dtos.bookingView(b, freshByBooking.get(b.id), false))
                .collect(Collectors.toList());
        return new Dtos.ReassignResult(version.id, version.versionNo, vehicle.id, vehicle.plateNumber,
                vehicle.model, views, manualReview, List.copyOf(splitGroups), warnings);
    }

    /** 调度手动调座：唯一约束保证并发下同一座位不会分给两个人 */
    @Transactional
    public Dtos.BookingView manualAssign(long versionId, long bookingId, String seatNo) {
        SeatVersion version = SeatVersion.findById(versionId);
        if (version == null) {
            throw new NotFoundException("座位版本不存在: " + versionId);
        }
        if (version.status != VersionStatus.ACTIVE) {
            throw new ClientErrorException("只能调整当前生效的座位版本", 409);
        }
        Assignment a = Assignment.find("version.id = ?1 and booking.id = ?2", versionId, bookingId).firstResult();
        if (a == null) {
            throw new NotFoundException("该乘客在此版本中没有分配记录");
        }
        Seat seat = Seat.find("vehicle.id = ?1 and seatNo = ?2", version.vehicle.id, seatNo).firstResult();
        if (seat == null) {
            throw new NotFoundException("座位不存在: " + seatNo);
        }
        Assignment occupant = Assignment.find("version.id = ?1 and seatNo = ?2", versionId, seatNo).firstResult();
        if (occupant != null && !occupant.booking.id.equals(bookingId)) {
            throw new ClientErrorException("座位 " + seatNo + " 已分配给 " + occupant.booking.passengerName, 409);
        }
        a.seatNo = seatNo;
        a.status = AssignmentStatus.PENDING;
        a.groupSplit = false;
        a.note = "调度手动调整，待乘客确认";
        a.persist();
        try {
            a.flush();
        } catch (PersistenceException e) {
            throw new ClientErrorException("座位 " + seatNo + " 被并发分配，请重试", 409);
        }
        boolean checkedIn = hasCheckIn(bookingId);
        return Dtos.bookingView(a.booking, a, checkedIn);
    }

    private static boolean hasCheckIn(long bookingId) {
        return com.busreseat.model.CheckIn.count("booking.id", bookingId) > 0;
    }

    private Assignment place(Booking b, Seat s, SeatVersion version,
                             Map<Long, Assignment> prevByBooking, Map<Long, Assignment> freshByBooking) {
        Assignment a = new Assignment();
        a.version = version;
        a.booking = b;
        a.seatNo = s.seatNo;
        Assignment prev = prevByBooking.get(b.id);
        // 座位号与上一已确认版本一致才继承确认状态，否则都要乘客重新确认
        a.status = prev != null && prev.status == AssignmentStatus.CONFIRMED
                && prev.seatNo != null && prev.seatNo.equals(s.seatNo)
                ? AssignmentStatus.CONFIRMED : AssignmentStatus.PENDING;
        a.persist();
        freshByBooking.put(b.id, a);
        return a;
    }

    private void toManualReview(Booking b, SeatVersion version, String note,
                                Map<Long, Assignment> freshByBooking, List<String> manualReview) {
        Assignment a = new Assignment();
        a.version = version;
        a.booking = b;
        a.seatNo = null;
        a.status = AssignmentStatus.MANUAL_REVIEW;
        a.note = note;
        a.persist();
        freshByBooking.put(b.id, a);
        manualReview.add(b.passengerName);
    }

    private static Integer seatRowOf(String seatNo, List<Seat> seats) {
        for (Seat s : seats) {
            if (s.seatNo.equals(seatNo)) {
                return s.rowIdx;
            }
        }
        return -1;
    }

    /** 座位占用辅助：从空闲座位里按规则取座 */
    private static final class Placer {
        private final List<Seat> free;

        Placer(List<Seat> seats) {
            this.free = new ArrayList<>(seats);
        }

        Seat takeKind(SeatKind kind) {
            for (int i = 0; i < free.size(); i++) {
                if (free.get(i).kind == kind) {
                    return free.remove(i);
                }
            }
            return null;
        }

        Seat takeFirstNormal() {
            for (int i = 0; i < free.size(); i++) {
                if (free.get(i).kind == SeatKind.NORMAL) {
                    return free.remove(i);
                }
            }
            return null;
        }

        Seat takeSeatNoIfNormal(String seatNo) {
            for (int i = 0; i < free.size(); i++) {
                Seat s = free.get(i);
                if (s.seatNo.equals(seatNo) && s.kind == SeatKind.NORMAL) {
                    return free.remove(i);
                }
            }
            return null;
        }

        /** 找 n 个同排且列号连续的空闲普通座；anchorRows 非空时只在这些排里找 */
        List<Seat> takeAdjacentRun(int n, Set<Integer> anchorRows) {
            Map<Integer, List<Seat>> byRow = new TreeMap<>();
            for (Seat s : free) {
                if (s.kind == SeatKind.NORMAL && (anchorRows.isEmpty() || anchorRows.contains(s.rowIdx))) {
                    byRow.computeIfAbsent(s.rowIdx, k -> new ArrayList<>()).add(s);
                }
            }
            for (Map.Entry<Integer, List<Seat>> row : byRow.entrySet()) {
                List<Seat> seats = row.getValue();
                seats.sort(Comparator.comparingInt(s -> s.colIdx));
                int start = 0;
                while (start + n <= seats.size()) {
                    boolean consecutive = true;
                    for (int i = 1; i < n; i++) {
                        if (seats.get(start + i).colIdx != seats.get(start).colIdx + i) {
                            consecutive = false;
                            break;
                        }
                    }
                    if (consecutive) {
                        List<Seat> run = new ArrayList<>(seats.subList(start, start + n));
                        free.removeAll(run);
                        return run;
                    }
                    start++;
                }
            }
            return null;
        }
    }
}
