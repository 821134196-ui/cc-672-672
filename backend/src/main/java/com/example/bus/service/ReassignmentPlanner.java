package com.example.bus.service;

import com.example.bus.domain.Passenger;
import com.example.bus.domain.SeatType;
import com.example.bus.domain.SpecialNeed;
import com.example.bus.model.SeatDef;
import com.example.bus.model.SeatMap;

import java.util.*;

/**
 * 座位重排算法（纯逻辑，不涉及数据库，便于单元测试）。
 *
 * 规则（按优先级）：
 * 1. 特殊需求优先：轮椅乘客占轮椅位、儿童座椅需求占儿童座椅位；
 *    新车没有对应座位类型时该乘客不安排座位，标记为“需转人工”。
 * 2. 同行人成组安排：优先在同一排找连续座位；找不到连续整段时，
 *    退化为“尽量靠近”的分散安排，并由计划结果标记 GROUP_SPLIT，
 *    让调度在问题列表中发现。
 * 3. 其余单人乘客按排/座顺序依次入座。
 * 4. 座位不足时多出的乘客不安排座位，标记 OVERFLOW（转人工）。
 */
public class ReassignmentPlanner {

    /** 计划中的一位乘客安排结果 */
    public record Placed(Passenger passenger, SeatDef seat) {}

    /** 计划中的一条问题 */
    public record Issue(com.example.bus.domain.IssueType type, Long passengerId,
                        Long groupId, String detail) {}

    /** 完整重排计划 */
    public static class Plan {
        public final Map<Long, SeatDef> seats = new LinkedHashMap<>();
        public final List<Issue> issues = new ArrayList<>();
        public final List<Passenger> unassigned = new ArrayList<>();

        public boolean allPlaced() {
            return unassigned.isEmpty();
        }
    }

    public Plan plan(SeatMap map, List<Passenger> passengers) {
        Plan plan = new Plan();
        Set<String> taken = new HashSet<>();
        List<Passenger> ordered = passengers.stream()
                .sorted(Comparator.comparingLong(p -> p.id))
                .toList();

        // ---- 1. 特殊需求乘客优先安排专用座位 ----
        for (Passenger p : ordered) {
            if (p.specialNeed == SpecialNeed.WHEELCHAIR) {
                SeatDef s = firstFree(map.byType(SeatType.WHEELCHAIR), taken);
                if (s != null) {
                    place(plan, taken, p, s);
                } else {
                    plan.issues.add(new Issue(com.example.bus.domain.IssueType.WHEELCHAIR_UNMET,
                            p.id, null, "乘客 " + p.name + " 需要轮椅位，但新车无可用轮椅位，转人工处理"));
                    plan.unassigned.add(p);
                }
            } else if (p.specialNeed == SpecialNeed.CHILD_SEAT) {
                SeatDef s = firstFree(map.byType(SeatType.CHILD), taken);
                if (s != null) {
                    place(plan, taken, p, s);
                } else {
                    plan.issues.add(new Issue(com.example.bus.domain.IssueType.CHILD_SEAT_UNMET,
                            p.id, null, "乘客 " + p.name + " 需要儿童座椅位，但新车无可用儿童座椅位，转人工处理"));
                    plan.unassigned.add(p);
                }
            }
        }

        // ---- 2. 同行人分组（组包含特殊需求成员：拆散检测以全组实际座位为准） ----
        // 第 1 步已升级人工（专用座无法满足）的乘客绝不自动安排其他座位，
        // 只能等待人工处理——即使他属于某个同行组。
        Set<Long> escalated = new HashSet<>();
        plan.unassigned.forEach(p -> escalated.add(p.id));
        Map<Long, List<Passenger>> groups = new LinkedHashMap<>();
        List<Passenger> singles = new ArrayList<>();
        for (Passenger p : ordered) {
            if (escalated.contains(p.id)) continue;
            if (p.group != null) {
                groups.computeIfAbsent(p.group.id, k -> new ArrayList<>()).add(p);
            } else if (p.specialNeed == SpecialNeed.NONE) {
                singles.add(p);
            }
            // 无组的特殊需求乘客已在第 1 步处理（入座或转人工），此处不重复
        }
        // 大组优先（先安顿最难安排的），组间按最早加入的乘客 id 排序保证确定性
        List<Map.Entry<Long, List<Passenger>>> groupEntries = new ArrayList<>(groups.entrySet());
        groupEntries.sort(Comparator
                .comparingInt((Map.Entry<Long, List<Passenger>> e) -> e.getValue().size()).reversed()
                .thenComparingLong(e -> e.getValue().stream().mapToLong(x -> x.id).min().orElse(0)));

        List<SeatDef> freeStandard = freeStandard(map, taken);

        for (Map.Entry<Long, List<Passenger>> entry : groupEntries) {
            // 已在第 1 步入座的同组成员（如孩子拿到儿童座椅位）作为“锚点”，
            // 选座时优先让其余成员与锚点保持相邻
            List<SeatDef> anchors = entry.getValue().stream()
                    .map(m -> plan.seats.get(m.id))
                    .filter(Objects::nonNull)
                    .toList();
            // 只安排尚未入座的成员（轮椅/儿童成员可能已在第 1 步拿到专用座）
            List<Passenger> members = entry.getValue().stream()
                    .filter(m -> !plan.seats.containsKey(m.id))
                    .sorted(Comparator.comparingLong(p -> p.id))
                    .toList();
            if (members.isEmpty()) continue;
            int need = members.size();

            // 2a. 找一段连续空座；有锚点时优先选能与锚点连成一体的窗口
            List<SeatDef> contiguous = findContiguousFreeRun(map, taken, need, anchors);
            if (contiguous != null) {
                for (int i = 0; i < need; i++) {
                    place(plan, taken, members.get(i), contiguous.get(i));
                }
            } else {
                // 2b. 找不到连续整段：挑选彼此最靠近的若干座位（尽量少拆散）
                List<SeatDef> clustered = pickClustered(freeStandard, need);
                if (clustered != null) {
                    for (int i = 0; i < need; i++) {
                        place(plan, taken, members.get(i), clustered.get(i));
                    }
                } else {
                    // 座位总数不足：组内成员按序安排，安排不下的进入 unassigned
                    int seatIdx = 0;
                    for (Passenger m : members) {
                        if (seatIdx < freeStandard.size()) {
                            place(plan, taken, m, freeStandard.get(seatIdx++));
                        } else {
                            plan.unassigned.add(m);
                        }
                    }
                }
            }
            // 刷新空闲座位视图
            freeStandard = freeStandard(map, taken);
        }

        // ---- 3. 单人乘客依次入座 ----
        freeStandard = freeStandard(map, taken);
        Iterator<SeatDef> seatIt = freeStandard.iterator();
        for (Passenger p : singles) {
            if (seatIt.hasNext()) {
                place(plan, taken, p, seatIt.next());
            } else {
                plan.issues.add(new Issue(com.example.bus.domain.IssueType.OVERFLOW,
                        p.id, null, "新车座位不足，乘客 " + p.name + " 未能安排座位，转人工处理"));
                plan.unassigned.add(p);
            }
        }

        // ---- 4. 组内因容量不足未安排的成员补 OVERFLOW 问题 ----
        for (Passenger p : plan.unassigned) {
            boolean already = plan.issues.stream().anyMatch(i -> Objects.equals(i.passengerId(), p.id));
            if (!already) {
                plan.issues.add(new Issue(com.example.bus.domain.IssueType.OVERFLOW,
                        p.id, p.group == null ? null : p.group.id,
                        "新车座位不足，乘客 " + p.name + " 未能安排座位，转人工处理"));
            }
        }

        // ---- 5. 权威检测：哪些组实际被拆散（与 2a/2b 的预判一致，手动调整后也可复用） ----
        detectSplitGroups(map, plan, groups);

        return plan;
    }

    /** 在当前安排结果上检测被拆散的同行组（静态方法，手动调座后也可调用） */
    public static void detectSplitGroups(SeatMap map, Plan plan, Map<Long, List<Passenger>> groups) {
        for (Map.Entry<Long, List<Passenger>> entry : groups.entrySet()) {
            List<Passenger> members = entry.getValue();
            List<SeatDef> got = members.stream()
                    .map(m -> plan.seats.get(m.id))
                    .filter(Objects::nonNull)
                    .toList();
            if (got.size() < 2) continue; // 单人或未安排的由其他问题类型覆盖
            if (!isContiguous(got)) {
                Long groupId = entry.getKey();
                boolean exists = plan.issues.stream()
                        .anyMatch(i -> i.type() == com.example.bus.domain.IssueType.GROUP_SPLIT
                                && Objects.equals(i.groupId(), groupId));
                if (!exists) {
                    String names = members.stream().map(m -> m.name).reduce((a, b) -> a + "、" + b).orElse("");
                    String seatCodes = got.stream().map(s -> s.code).reduce((a, b) -> a + "/" + b).orElse("");
                    plan.issues.add(new Issue(com.example.bus.domain.IssueType.GROUP_SPLIT,
                            null, groupId,
                            "同行人（" + names + "）未能安排在连续座位，当前座位：" + seatCodes + "，请调度关注"));
                }
            }
        }
    }

    private void place(Plan plan, Set<String> taken, Passenger p, SeatDef s) {
        plan.seats.put(p.id, s);
        taken.add(s.code);
    }

    private SeatDef firstFree(List<SeatDef> seats, Set<String> taken) {
        return seats.stream().filter(s -> !taken.contains(s.code)).findFirst().orElse(null);
    }

    private List<SeatDef> freeStandard(SeatMap map, Set<String> taken) {
        return map.byType(SeatType.STANDARD).stream()
                .filter(s -> !taken.contains(s.code))
                .toList();
    }

    /**
     * 在空闲普通座中找长度 >= need 的连续段。
     * 若提供锚点（已入座的同组特殊需求成员），则在所有可行窗口中优先选择
     * 与锚点同属一个连通分量的窗口，保证整家人真正相邻。
     */
    private List<SeatDef> findContiguousFreeRun(SeatMap map, Set<String> taken, int need,
                                                List<SeatDef> anchors) {
        List<SeatDef> fallback = null;
        for (List<SeatDef> run : map.contiguousRuns()) {
            List<SeatDef> free = run.stream().filter(s -> !taken.contains(s.code)).toList();
            for (int start = 0; start + need <= free.size(); start++) {
                List<SeatDef> window = new ArrayList<>(free.subList(start, start + need));
                if (anchors.isEmpty()) return window;
                if (fallback == null) fallback = window;
                List<SeatDef> all = new ArrayList<>(anchors);
                all.addAll(window);
                if (isContiguous(all)) return window;
            }
        }
        return fallback;
    }

    /**
     * 从空闲座位中挑选 need 个彼此最靠近的座位（以每个候选座位为锚点，
     * 取网格距离最近的 need 个，选总距离最小方案）。
     */
    private List<SeatDef> pickClustered(List<SeatDef> free, int need) {
        if (free.size() < need) return null;
        List<SeatDef> best = null;
        int bestCost = Integer.MAX_VALUE;
        for (SeatDef anchor : free) {
            List<SeatDef> nearest = free.stream()
                    .sorted(Comparator.comparingInt((SeatDef s) -> SeatMap.gridDistance(anchor, s))
                            .thenComparing(s -> s.code))
                    .limit(need)
                    .sorted(Comparator.comparingInt((SeatDef s) -> s.row).thenComparingInt(s -> s.order))
                    .toList();
            int cost = spreadCost(nearest);
            if (cost < bestCost) {
                bestCost = cost;
                best = new ArrayList<>(nearest);
            }
        }
        return best;
    }

    /** 一组座位的“分散代价”：相邻排/序差之和（连续座位代价为 need-1） */
    private int spreadCost(List<SeatDef> seats) {
        List<SeatDef> sorted = seats.stream()
                .sorted(Comparator.comparingInt((SeatDef s) -> s.row).thenComparingInt(s -> s.order))
                .toList();
        int cost = 0;
        for (int i = 1; i < sorted.size(); i++) {
            SeatDef a = sorted.get(i - 1);
            SeatDef b = sorted.get(i);
            if (a.row == b.row) {
                cost += b.order - a.order;
            } else {
                // 跨排视为较大的代价
                cost += (b.row - a.row) * 10 + Math.abs(b.order - a.order);
            }
        }
        return cost;
    }

    /**
     * 判断整组是否“坐在一起”：以座位为节点，同排 order 相差 1 互为连通边
     * （轮椅位/儿童座椅位嵌入排内顺序时，与相邻普通座自然连通）；
     * 所有成员座位必须属于同一个连通分量。
     * 跨排不算相邻——物理上前后排座位不挨在一起。
     */
    private static boolean isContiguous(List<SeatDef> seats) {
        if (seats.size() <= 1) return true;
        int n = seats.size();
        boolean[] visited = new boolean[n];
        java.util.Deque<Integer> stack = new ArrayDeque<>();
        stack.push(0);
        visited[0] = true;
        int count = 1;
        while (!stack.isEmpty()) {
            int i = stack.pop();
            for (int j = 0; j < n; j++) {
                if (visited[j]) continue;
                SeatDef a = seats.get(i);
                SeatDef b = seats.get(j);
                if (a.row == b.row && Math.abs(a.order - b.order) == 1) {
                    visited[j] = true;
                    count++;
                    stack.push(j);
                }
            }
        }
        return count == n;
    }
}
