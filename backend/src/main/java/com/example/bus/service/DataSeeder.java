package com.example.bus.service;

import com.example.bus.domain.*;
import com.example.bus.model.SeatDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动种子数据：
 * - 原车 A-38（38 座）及 3 辆可调配新车（含“无轮椅位中巴”和“窄排小客”用于演示转人工与拆散）
 * - 12 名已售票乘客：一个三口之家、一对同事、一位轮椅乘客、一位儿童座椅需求乘客等
 * - 初始座位版本 v1（原车），全员已确认
 */
@ApplicationScoped
public class DataSeeder {

    @Inject
    ObjectMapper mapper;

    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        if (Vehicle.count() > 0) return;
        seed();
    }

    /** 清空并重建演示数据（POST /api/admin/reset 调用，便于反复演示） */
    @Transactional
    public void reset() {
        BoardingRecord.deleteAll();
        SeatAssignment.deleteAll();
        ReassignmentIssue.deleteAll();
        SeatVersion.deleteAll();
        SmsMessage.deleteAll();
        DispatchLog.deleteAll();
        Passenger.deleteAll();
        TravelGroup.deleteAll();
        Vehicle.deleteAll();
        seed();
    }

    private void seed() {
        // ---------- 车辆座位图 ----------
        Vehicle a38 = vehicle("A-38", "原车 38 座大巴", false, buildStandardBus(9, true, true));
        // 9 排 ×4 = 36 普通座 + 1 轮椅位 + 1 儿童座椅位 = 38

        Vehicle b45 = vehicle("B-45", "45 座豪华大巴", true, buildB45());
        // 10 排 ×4 + 第 11 排 3 座 = 43 普通座 + W1 + C1 = 45

        Vehicle c30 = vehicle("C-30", "30 座中巴（无轮椅位/儿童座椅）", true, buildPlainRows());
        // 30 普通座（第 8 排只有 2 座）：轮椅与儿童需求都无法满足

        Vehicle e15 = vehicle("E-15", "15 座窄排小客", true, buildNarrowBus());
        // 每排 [2 普通座, 1 轮椅位, 2 普通座] ×3 = 12 普通座 + 3 轮椅位，无儿童座椅位

        // ---------- 同行组 ----------
        TravelGroup family = new TravelGroup();
        family.name = "张家三口";
        family.persist();
        TravelGroup coworkers = new TravelGroup();
        coworkers.name = "王强-赵敏 同行";
        coworkers.persist();

        // ---------- 乘客（13 人） ----------
        List<Passenger> passengers = List.of(
                passenger("张伟", "13800000001", "1A", SpecialNeed.NONE, family),
                passenger("李娟", "13800000002", "1B", SpecialNeed.NONE, family),
                passenger("张小明", "13800000003", "1C", SpecialNeed.CHILD_SEAT, family),
                passenger("张兰", "13800000004", "1D", SpecialNeed.NONE, family),
                passenger("王强", "13800000005", "2A", SpecialNeed.NONE, coworkers),
                passenger("赵敏", "13800000006", "2B", SpecialNeed.NONE, coworkers),
                passenger("陈建国", "13800000007", "3A", SpecialNeed.WHEELCHAIR, null),
                passenger("刘芳", "13800000008", "3B", SpecialNeed.NONE, null),
                passenger("周杰", "13800000009", "4A", SpecialNeed.NONE, null),
                passenger("吴秀英", "13800000010", "4B", SpecialNeed.NONE, null),
                passenger("郑浩", "13800000011", "5A", SpecialNeed.NONE, null),
                passenger("孙丽", "13800000012", "5B", SpecialNeed.NONE, null),
                passenger("钱多", "13800000013", "6A", SpecialNeed.NONE, null)
        );

        // ---------- 初始版本 v1：原车原座，全员已确认（售票时的既定状态） ----------
        SeatVersion v1 = new SeatVersion();
        v1.vehicle = a38;
        v1.versionNo = 1;
        v1.status = VersionStatus.ACTIVE;
        v1.activeFlag = 1;
        v1.createdAt = Instant.now();
        v1.reason = "初始车辆与票面座位";
        v1.persist();

        var map = new com.example.bus.model.SeatMap(
                com.example.bus.model.SeatMap.parse(a38.seatMap, mapper).all());
        for (Passenger p : passengers) {
            com.example.bus.model.SeatDef sd = map.byCode(p.originalSeat)
                    .orElseThrow(() -> new IllegalStateException("种子数据座位不存在: " + p.originalSeat));
            SeatAssignment a = new SeatAssignment();
            a.version = v1;
            a.passenger = p;
            a.seatCode = sd.code;
            a.seatType = sd.type;
            a.status = AssignmentStatus.CONFIRMED;
            a.confirmedAt = Instant.now();
            a.persist();
        }
    }

    private Vehicle vehicle(String code, String name, boolean available, List<SeatDef> seats) {
        try {
            Vehicle v = new Vehicle();
            v.code = code;
            v.name = name;
            v.available = available;
            v.seatMap = mapper.writeValueAsString(seats);
            v.persist();
            return v;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Passenger passenger(String name, String phone, String originalSeat,
                                SpecialNeed need, TravelGroup group) {
        Passenger p = new Passenger();
        p.name = name;
        p.phone = phone;
        p.originalSeat = originalSeat;
        p.specialNeed = need;
        p.group = group;
        p.persist();
        return p;
    }

    private static final String[] COLS = {"A", "B", "C", "D"};

    /**
     * 标准大巴：前排（第 1 排）从左到右依次为
     * [W1 轮椅位][C1 儿童座椅位][1A][1B][1C][1D]，
     * 其余各排为 [rA][rB][rC][rD]。order 全局连续，
     * 因此孩子的儿童座椅位与父母普通座可以真正相邻。
     * 座位总数 = 2（专用座） + 4×rows。
     */
    private List<SeatDef> buildStandardBus(int rows, boolean wheelchair, boolean child) {
        List<SeatDef> seats = new ArrayList<>();
        int order = 1;
        if (wheelchair) seats.add(new SeatDef("W1", 1, "W", order++, SeatType.WHEELCHAIR));
        if (child) seats.add(new SeatDef("C1", 1, "C", order++, SeatType.CHILD));
        for (int c = 0; c < 4; c++) {
            seats.add(new SeatDef(1 + COLS[c], 1, COLS[c], order++, SeatType.STANDARD));
        }
        for (int r = 2; r <= rows; r++) {
            for (int c = 0; c < 4; c++) {
                seats.add(new SeatDef(r + COLS[c], r, COLS[c], order++, SeatType.STANDARD));
            }
        }
        return seats;
    }

    /** B-45：标准 9 排布局（38）+ 第 10 排 4 座 + 第 11 排 3 座 = 45 */
    private List<SeatDef> buildB45() {
        List<SeatDef> seats = buildStandardBus(9, true, true);
        int order = seats.stream().mapToInt(s -> s.order).max().orElse(0) + 1;
        for (int c = 0; c < 4; c++) {
            seats.add(new SeatDef(10 + COLS[c], 10, COLS[c], order++, SeatType.STANDARD));
        }
        for (int c = 0; c < 3; c++) {
            seats.add(new SeatDef(11 + COLS[c], 11, COLS[c], order++, SeatType.STANDARD));
        }
        return seats;
    }

    /** 全普通座 30 座：前 7 排每排 4 座，第 8 排只有 A/B 两座（无轮椅位/儿童座椅位） */
    private List<SeatDef> buildPlainRows() {
        List<SeatDef> seats = new ArrayList<>();
        int order = 1;
        for (int r = 1; r <= 8; r++) {
            int perRow = r == 8 ? 2 : 4;
            for (int c = 0; c < perRow; c++) {
                seats.add(new SeatDef(r + COLS[c], r, COLS[c], order++, SeatType.STANDARD));
            }
            // order 跨排连续：末排缺座时，C/D 的顺序号被跳过，同排连续段判定仍正确
            order += 4 - perRow;
        }
        return seats;
    }

    /**
     * 窄排小客：每排布局 [普通, 普通, 轮椅位, 普通, 普通]，
     * 普通座连续段最长只有 2 —— 三口及以上的同行人组必然被拆散。
     */
    private List<SeatDef> buildNarrowBus() {
        List<SeatDef> seats = new ArrayList<>();
        int order = 1;
        for (int r = 1; r <= 3; r++) {
            seats.add(new SeatDef(r + "A", r, "A", order++, SeatType.STANDARD));
            seats.add(new SeatDef(r + "B", r, "B", order++, SeatType.STANDARD));
            seats.add(new SeatDef("W" + r, r, "W", order++, SeatType.WHEELCHAIR));
            seats.add(new SeatDef(r + "C", r, "C", order++, SeatType.STANDARD));
            seats.add(new SeatDef(r + "D", r, "D", order++, SeatType.STANDARD));
        }
        return seats;
    }
}
