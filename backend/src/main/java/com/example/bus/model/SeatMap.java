package com.example.bus.model;

import com.example.bus.domain.SeatType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 座位图：从车辆 seatMap JSON 解析，提供按类型取座、同排连续座位段等查询。
 * “相邻”定义：同一排内 order 连续（跨过道也算同一连续段，只是隔了过道）。
 */
public class SeatMap {

    private final List<SeatDef> seats;
    private final Map<String, SeatDef> byCode;

    public SeatMap(List<SeatDef> seats) {
        this.seats = seats;
        this.byCode = seats.stream().collect(Collectors.toMap(s -> s.code, s -> s));
    }

    public static SeatMap parse(String json, ObjectMapper mapper) {
        try {
            List<SeatDef> defs = mapper.readValue(json, new TypeReference<List<SeatDef>>() {});
            return new SeatMap(defs);
        } catch (Exception e) {
            throw new IllegalStateException("座位图解析失败: " + e.getMessage(), e);
        }
      }

    public List<SeatDef> all() {
        return seats;
    }

    public Optional<SeatDef> byCode(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public List<SeatDef> byType(SeatType type) {
        return seats.stream().filter(s -> s.type == type)
                .sorted(Comparator.comparingInt((SeatDef s) -> s.row).thenComparingInt(s -> s.order))
                .toList();
    }

    public int countByType(SeatType type) {
        return (int) seats.stream().filter(s -> s.type == type).count();
    }

    public int totalSeats() {
        return seats.size();
    }

    /**
     * 返回指定排内、order 连续的普通座分段（长度 1..n），用于同行人成组安排。
     * 仅统计 STANDARD 座位；特殊座不参与家庭组安排。
     */
    public List<List<SeatDef>> contiguousRuns() {
        List<List<SeatDef>> runs = new ArrayList<>();
        Map<Integer, List<SeatDef>> byRow = seats.stream()
                .filter(s -> s.type == SeatType.STANDARD)
                .collect(Collectors.groupingBy(s -> s.row));
        for (var entry : byRow.entrySet()) {
            List<SeatDef> rowSeats = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(s -> s.order))
                    .toList();
            List<SeatDef> run = new ArrayList<>();
            SeatDef prev = null;
            for (SeatDef s : rowSeats) {
                if (prev != null && s.order == prev.order + 1) {
                    run.add(s);
                } else {
                    if (!run.isEmpty()) runs.add(run);
                    run = new ArrayList<>(List.of(s));
                }
                prev = s;
            }
            if (!run.isEmpty()) runs.add(run);
        }
        return runs;
    }

    /** 网格距离：排差 + 排内顺序差，用于拆散后“尽量靠近”的补救安排 */
    public static int gridDistance(SeatDef a, SeatDef b) {
        return Math.abs(a.row - b.row) * 3 + Math.abs(a.order - b.order);
    }
}
