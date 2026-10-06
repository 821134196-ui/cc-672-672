package com.example.bus.service;

import com.example.bus.domain.Passenger;
import com.example.bus.domain.SeatType;
import com.example.bus.domain.SpecialNeed;
import com.example.bus.domain.TravelGroup;
import com.example.bus.model.SeatDef;
import com.example.bus.model.SeatMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯算法单元测试：同行人相邻、拆散标记、特殊需求无法满足、超载。
 * 直接构造内存座位图与乘客（手动赋 id），不依赖数据库。
 */
class ReassignmentPlannerTest {

    private ReassignmentPlanner planner = new ReassignmentPlanner();

    private SeatDef seat(String code, int row, int order, SeatType type) {
        return new SeatDef(code, row, code.substring(code.length() - 1), order, type);
    }

    /** 全普通座，rows 排 ×4 */
    private SeatMap standardMap(int rows) {
        List<SeatDef> seats = new ArrayList<>();
        int o = 1;
        for (int r = 1; r <= rows; r++) {
            for (String c : new String[]{"A", "B", "C", "D"}) {
                seats.add(seat(r + c, r, o++, SeatType.STANDARD));
            }
        }
        return new SeatMap(seats);
    }

    private Passenger p(long id, String name, SpecialNeed need, TravelGroup g) {
        Passenger p = new Passenger();
        p.id = id;
        p.name = name;
        p.originalSeat = "X" + id;
        p.specialNeed = need;
        p.group = g;
        return p;
    }

    private TravelGroup g(long id, String name) {
        TravelGroup g = new TravelGroup();
        g.id = id;
        g.name = name;
        return g;
    }

    @Test
    void 同行人应安排在连续座位() {
        TravelGroup family = g(100, "家庭");
        List<Passenger> ps = List.of(
                p(1, "甲", SpecialNeed.NONE, family),
                p(2, "乙", SpecialNeed.NONE, family),
                p(3, "丙", SpecialNeed.NONE, family),
                p(4, "丁", SpecialNeed.NONE, null));
        var plan = planner.plan(standardMap(3), ps);
        assertEquals(4, plan.seats.size());
        assertTrue(plan.issues.stream().noneMatch(i -> i.type() == com.example.bus.domain.IssueType.GROUP_SPLIT),
                "座位充足且存在连续段时不应报告拆散");
        List<String> familySeats = List.of(plan.seats.get(1L).code, plan.seats.get(2L).code, plan.seats.get(3L).code);
        // 三人应在同一排
        assertTrue(familySeats.stream().map(s -> s.charAt(0)).distinct().count() == 1,
                "家庭应同排就座，实际: " + familySeats);
    }

    @Test
    void 连续段不足时应标记拆散() {
        // 每排 [普通,普通,轮椅位,普通,普通]：普通座连续段最长 2
        List<SeatDef> defs = new ArrayList<>();
        int o = 1;
        for (int r = 1; r <= 3; r++) {
            defs.add(seat(r + "A", r, o++, SeatType.STANDARD));
            defs.add(seat(r + "B", r, o++, SeatType.STANDARD));
            defs.add(seat("W" + r, r, o++, SeatType.WHEELCHAIR));
            defs.add(seat(r + "C", r, o++, SeatType.STANDARD));
            defs.add(seat(r + "D", r, o++, SeatType.STANDARD));
        }
        TravelGroup family = g(100, "家庭");
        List<Passenger> ps = List.of(
                p(1, "甲", SpecialNeed.NONE, family),
                p(2, "乙", SpecialNeed.NONE, family),
                p(3, "丙", SpecialNeed.NONE, family));
        var plan = planner.plan(new SeatMap(defs), ps);
        assertTrue(plan.issues.stream().anyMatch(i -> i.type() == com.example.bus.domain.IssueType.GROUP_SPLIT),
                "无法连续就座时必须产生 GROUP_SPLIT 问题");
    }

    @Test
    void 轮椅位不足应转人工且不自动改派普通座() {
        TravelGroup g = g(100, "G");
        List<Passenger> ps = List.of(
                p(1, "轮椅乘客", SpecialNeed.WHEELCHAIR, g),
                p(2, "家属", SpecialNeed.NONE, g));
        var plan = planner.plan(standardMap(2), ps);
        assertNull(plan.seats.get(1L), "无轮椅位时不应给轮椅乘客分配普通座");
        assertTrue(plan.unassigned.stream().anyMatch(x -> x.id == 1L));
        assertTrue(plan.issues.stream().anyMatch(i -> i.type() == com.example.bus.domain.IssueType.WHEELCHAIR_UNMET));
    }

    @Test
    void 儿童座椅不足应转人工且家属仍正常安排() {
        TravelGroup family = g(100, "家庭");
        List<Passenger> ps = List.of(
                p(1, "孩子", SpecialNeed.CHILD_SEAT, family),
                p(2, "家长", SpecialNeed.NONE, family));
        var plan = planner.plan(standardMap(2), ps);
        assertNull(plan.seats.get(1L), "无儿童座椅位时孩子不自动改派");
        assertNotNull(plan.seats.get(2L), "家长仍应安排普通座");
        assertTrue(plan.issues.stream().anyMatch(i -> i.type() == com.example.bus.domain.IssueType.CHILD_SEAT_UNMET));
    }

    @Test
    void 座位不足应产生超载问题() {
        List<Passenger> ps = List.of(
                p(1, "甲", SpecialNeed.NONE, null),
                p(2, "乙", SpecialNeed.NONE, null),
                p(3, "丙", SpecialNeed.NONE, null));
        var plan = planner.plan(standardMap(0), ps); // 0 排 = 无座
        assertEquals(0, plan.seats.size());
        assertEquals(3, plan.issues.stream().filter(i -> i.type() == com.example.bus.domain.IssueType.OVERFLOW).count());
    }
}
