package com.example.bus.service;

import com.example.bus.domain.*;
import com.example.bus.model.SeatMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 座位领域服务：换车重排、乘客确认、司机检票。
 *
 * 并发策略：
 * - 换车是“读当前活动版本 -> 生成新版本”的临界区。SQLite 连接池配置为 1，
 *   所有写事务天然串行执行；
 * - 同时要求请求方携带 expectedVersionNo（乐观版本号），与当前活动版本不一致即 409；
 * - 数据库层再设双保险：seat_version.active_flag 唯一（至多一个活动版本）、
 *   seat_assignment(version_id, seat_code) 唯一（同版本一座一人）。
 */
@ApplicationScoped
public class SeatService {

    @Inject
    ObjectMapper mapper;

    @Inject
    MockSmsService smsService;

    @Inject
    MockDispatchService dispatchService;

    /**
     * 临时换车并重新排座。
     *
     * @param operator          调度标识
     * @param newVehicleCode    新车代码
     * @param expectedVersionNo 调度页面当前看到的版本号（为空表示当前无版本）
     */
    @Transactional
    public Map<String, Object> swapVehicle(String operator, String newVehicleCode,
                                           Integer expectedVersionNo, String reason) {
        // 1. 读取并锁定当前活动版本（连接池=1 时写事务已串行，这里再加显式行锁）
        SeatVersion current = SeatVersion.findActive();
        if (current != null) {
            SeatVersion.find("id", current.id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
            current = SeatVersion.findActive();
        }
        Integer currentNo = current == null ? null : current.versionNo;
        if (!Objects.equals(currentNo, expectedVersionNo)) {
            throw new StaleVersionException(String.format(
                    "座位版本已过期：您基于 v%s 操作，但当前已是 v%s，请刷新后重试",
                    expectedVersionNo == null ? "无" : expectedVersionNo,
                    currentNo == null ? "无" : currentNo));
        }

        Vehicle vehicle = Vehicle.findByCode(newVehicleCode);
        if (vehicle == null) {
            throw new BusinessConflictException("车辆不存在: " + newVehicleCode);
        }
        SeatMap seatMap = SeatMap.parse(vehicle.seatMap, mapper);

        List<Passenger> passengers = Passenger.findAll().list();
        int newVersionNo = (currentNo == null ? 0 : currentNo) + 1;

        // 2. 纯算法重排
        ReassignmentPlanner planner = new ReassignmentPlanner();
        ReassignmentPlanner.Plan plan = planner.plan(seatMap, passengers);

        // 3. 旧版本失效；插入新的活动版本（active_flag 唯一约束兜底）
        if (current != null) {
            current.status = VersionStatus.SUPERSEDED;
            current.activeFlag = null;
            // 显式刷库：Hibernate 默认“先插入后更新”，不先刷出这条 UPDATE，
            // 新版本 activeFlag=1 的 INSERT 会触发唯一约束冲突
            current.flush();
        }
        SeatVersion version = new SeatVersion();
        version.vehicle = vehicle;
        version.versionNo = newVersionNo;
        version.status = VersionStatus.ACTIVE;
        version.activeFlag = 1;
        version.createdAt = Instant.now();
        version.reason = reason == null || reason.isBlank()
                ? "临时更换为 " + vehicle.code + "（" + vehicle.name + "）" : reason;
        version.persist();

        // 4. 落库分配（全部为 PROPOSED，等待乘客逐一确认）
        for (Map.Entry<Long, com.example.bus.model.SeatDef> e : plan.seats.entrySet()) {
            Passenger p = Passenger.findById(e.getKey());
            com.example.bus.model.SeatDef sd = e.getValue();
            SeatAssignment a = new SeatAssignment();
            a.version = version;
            a.passenger = p;
            a.seatCode = sd.code;
            a.seatType = sd.type;
            a.status = AssignmentStatus.PROPOSED;
            a.persist();
        }

        // 5. 问题落库（转人工 / 拆散提醒）
        for (ReassignmentPlanner.Issue issue : plan.issues) {
            ReassignmentIssue ri = new ReassignmentIssue();
            ri.version = version;
            ri.type = issue.type();
            ri.passenger = issue.passengerId() == null ? null : Passenger.findById(issue.passengerId());
            ri.group = issue.groupId() == null ? null : TravelGroup.findById(issue.groupId());
            ri.detail = issue.detail();
            ri.state = "OPEN";
            ri.persist();
        }

        // 6. 模拟接口：车辆调度工单 + 给每位已安排乘客发送变更短信
        dispatchService.logSwap(current == null ? null : current.vehicle.code,
                vehicle.code, newVersionNo, operator, version.reason);
        for (Long passengerId : plan.seats.keySet()) {
            Passenger p = Passenger.findById(passengerId);
            String seat = plan.seats.get(passengerId).code;
            String content = "【客运通知】车辆临时更换为 " + vehicle.code
                    + "，您的新座位为 " + seat + "（原座 " + p.originalSeat
                    + "），请在乘客页面确认。";
            smsService.send(p.id, p.phone, newVersionNo, content);
        }

        return buildManifest(version);
    }

    /** 乘客确认新座位（必须针对当前活动版本） */
    @Transactional
    public Map<String, Object> confirm(Long passengerId, Integer expectedVersionNo) {
        SeatVersion active = SeatVersion.findActive();
        if (active == null) {
            throw new BusinessConflictException("当前尚无座位版本，无法确认");
        }
        if (expectedVersionNo != null && expectedVersionNo != active.versionNo) {
            throw new StaleVersionException("座位版本已更新（当前 v" + active.versionNo + "），请刷新后重新确认");
        }
        SeatAssignment a = findAssignment(active.id, passengerId);
        if (a == null) {
            throw new BusinessConflictException("当前版本未为该乘客安排座位（可能需转人工处理），无法确认");
        }
        if (a.status != AssignmentStatus.CONFIRMED) {
            a.status = AssignmentStatus.CONFIRMED;
            a.confirmedAt = Instant.now();
        }
        return passengerView(passengerId);
    }

    /**
     * 司机检票。硬性规则：
     * 乘客必须在当前活动版本中已确认新座位，否则拒绝检票——
     * “乘客未确认新座位前，不能记为已经登车”。
     */
    @Transactional
    public Map<String, Object> board(Long passengerId, String operator) {
        SeatVersion active = SeatVersion.findActive();
        if (active == null) {
            throw new BusinessConflictException("当前尚无座位版本，无法检票");
        }
        SeatAssignment a = findAssignment(active.id, passengerId);
        if (a == null) {
            throw new BusinessConflictException("该乘客在当前名单中无座位（疑似转人工处理中），禁止检票");
        }
        if (a.status != AssignmentStatus.CONFIRMED) {
            throw new BusinessConflictException(
                    "乘客尚未确认新座位 " + a.seatCode + "，不能检票登车");
        }
        BoardingRecord existing = BoardingRecord.find(
                "passenger.id = ?1 and versionNo = ?2", passengerId, active.versionNo).firstResult();
        if (existing != null) {
            throw new BusinessConflictException("该乘客在本版本已检票，请勿重复检票");
        }
        BoardingRecord rec = new BoardingRecord();
        rec.passenger = Passenger.findById(passengerId);
        rec.boardedAt = Instant.now();
        rec.boardedBy = operator == null ? "driver" : operator;
        rec.versionNo = active.versionNo;
        rec.persist();
        return manifest();
    }

    /** 撤销检票（演示/纠错用，历史记录仍然保留在数据库中由版本链可追溯） */
    @Transactional
    public Map<String, Object> unboard(Long passengerId) {
        SeatVersion active = SeatVersion.findActive();
        if (active == null) throw new BusinessConflictException("当前尚无座位版本");
        BoardingRecord rec = BoardingRecord.find(
                "passenger.id = ?1 and versionNo = ?2", passengerId, active.versionNo).firstResult();
        if (rec != null) rec.delete();
        return manifest();
    }

    @Transactional
    public Map<String, Object> updateIssueState(Long issueId, String state) {
        ReassignmentIssue issue = ReassignmentIssue.findById(issueId);
        if (issue == null) throw new BusinessConflictException("问题不存在: " + issueId);
        if (!Set.of("OPEN", "RESOLVED", "ESCALATED").contains(state)) {
            throw new BusinessConflictException("非法状态: " + state);
        }
        issue.state = state;
        return Map.of("id", issue.id, "state", issue.state);
    }

    // ---------------- 查询（只读） ----------------

    public SeatAssignment findAssignment(Long versionId, Long passengerId) {
        return SeatAssignment.find("version.id = ?1 and passenger.id = ?2",
                versionId, passengerId).firstResult();
    }

    @Transactional
    public Map<String, Object> manifest() {
        SeatVersion active = SeatVersion.findActive();
        if (active == null) {
            return Map.of("version", null, "vehicle", null, "assignments", List.of(), "issues", List.of());
        }
        return buildManifest(active);
    }

    /** 指定历史版本的名单快照（连续换车后可回看，检票记录按版本保留） */
    @Transactional
    public Map<String, Object> versionDetail(int versionNo) {
        SeatVersion version = SeatVersion.find("versionNo", versionNo).firstResult();
        if (version == null) {
            throw new BusinessConflictException("版本不存在: v" + versionNo);
        }
        return buildManifest(version);
    }

    /** 当前活动版本完整名单 —— 乘客页与司机页读的是同一份数据，保证两边一致 */
    private Map<String, Object> buildManifest(SeatVersion version) {
        List<SeatAssignment> assignments = SeatAssignment.find("version.id = ?1 order by seatCode",
                version.id).list();
        List<ReassignmentIssue> issues = ReassignmentIssue.find("version.id = ?1 order by id",
                version.id).list();
        Set<Long> boardedThisVersion = new HashSet<>(BoardingRecord.find("versionNo", version.versionNo)
                .<BoardingRecord>list().stream().map(r -> r.passenger.id).toList());
        Set<Long> assignedPassengerIds = new HashSet<>(assignments.stream().map(a -> a.passenger.id).toList());

        List<Map<String, Object>> assignmentDtos = new ArrayList<>();
        for (SeatAssignment a : assignments) {
            Passenger p = a.passenger;
            assignmentDtos.add(assignmentDto(p, a.seatCode, a.seatType.name(), a.status.name(),
                    a.confirmedAt == null ? "" : a.confirmedAt.toString(),
                    boardedThisVersion.contains(p.id)));
        }

        // 已售票但本版本未安排座位的乘客（转人工对象）也必须出现在名单视图中
        for (Passenger p : Passenger.<Passenger>findAll().list()) {
            if (!assignedPassengerIds.contains(p.id)) {
                assignmentDtos.add(assignmentDto(p, "", "", "UNASSIGNED", "", false));
            }
        }

        List<Map<String, Object>> issueDtos = issues.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.id);
            m.put("versionNo", version.versionNo);
            m.put("type", i.type.name());
            m.put("passengerName", i.passenger == null ? "" : i.passenger.name);
            m.put("groupName", i.group == null ? "" : i.group.name);
            m.put("detail", i.detail);
            m.put("state", i.state);
            return m;
        }).toList();

        long confirmed = assignments.stream().filter(a -> a.status == AssignmentStatus.CONFIRMED).count();

        List<Map<String, Object>> seatMapDto = SeatMap.parse(version.vehicle.seatMap, mapper).all().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", s.code);
                    m.put("row", s.row);
                    m.put("col", s.col);
                    m.put("order", s.order);
                    m.put("type", s.type.name());
                    return m;
                }).toList();

        return Map.of(
                "version", Map.of(
                        "versionNo", version.versionNo,
                        "status", version.status.name(),
                        "reason", version.reason == null ? "" : version.reason,
                        "createdAt", version.createdAt.toString()
                ),
                "vehicle", Map.of(
                        "code", version.vehicle.code,
                        "name", version.vehicle.name,
                        "seatMap", seatMapDto
                ),
                "stats", Map.of(
                        "total", assignmentDtos.size(),
                        "assigned", assignments.size(),
                        "confirmed", confirmed,
                        "boarded", boardedThisVersion.size(),
                        "openIssues", issues.stream().filter(i -> "OPEN".equals(i.state)).count()
                ),
                "assignments", assignmentDtos,
                "issues", issueDtos
        );
    }

    /** 乘客视图：原座 / 新座并列 + 确认状态 + 当前版本检票状态 */
    @Transactional
    public Map<String, Object> passengerView(Long passengerId) {
        Passenger p = Passenger.findById(passengerId);
        if (p == null) throw new BusinessConflictException("乘客不存在: " + passengerId);
        SeatVersion active = SeatVersion.findActive();
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("passengerId", p.id);
        dto.put("name", p.name);
        dto.put("phone", p.phone == null ? "" : p.phone);
        dto.put("specialNeed", p.specialNeed.name());
        dto.put("groupName", p.group == null ? "" : p.group.name);
        dto.put("originalSeat", p.originalSeat);
        if (active == null) {
            dto.put("versionNo", null);
            dto.put("newSeat", "");
            dto.put("status", "NO_VERSION");
            dto.put("boarded", false);
            return dto;
        }
        SeatAssignment a = findAssignment(active.id, passengerId);
        dto.put("versionNo", active.versionNo);
        dto.put("vehicleCode", active.vehicle.code);
        dto.put("vehicleName", active.vehicle.name);
        dto.put("reason", active.reason);
        if (a == null) {
            dto.put("newSeat", "");
            dto.put("status", "UNASSIGNED");
            dto.put("boarded", false);
        } else {
            dto.put("newSeat", a.seatCode);
            dto.put("seatType", a.seatType.name());
            dto.put("status", a.status.name());
            dto.put("confirmedAt", a.confirmedAt == null ? "" : a.confirmedAt.toString());
            BoardingRecord br = BoardingRecord.find(
                    "passenger.id = ?1 and versionNo = ?2", passengerId, active.versionNo).firstResult();
            dto.put("boarded", br != null);
        }
        return dto;
    }

    /** 构造名单行 DTO（12 个字段，超过 Map.of 的 10 对上限，统一在此拼装） */
    private Map<String, Object> assignmentDto(Passenger p, String newSeat, String seatType,
                                              String status, String confirmedAt, boolean boarded) {
        TravelGroup g = p.group;
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("passengerId", p.id);
        dto.put("name", p.name);
        dto.put("phone", p.phone == null ? "" : p.phone);
        dto.put("originalSeat", p.originalSeat);
        dto.put("newSeat", newSeat);
        dto.put("seatType", seatType);
        dto.put("specialNeed", p.specialNeed.name());
        dto.put("groupId", g == null ? -1L : g.id);
        dto.put("groupName", g == null ? "" : g.name);
        dto.put("status", status);
        dto.put("confirmedAt", confirmedAt);
        dto.put("boarded", boarded);
        return dto;
    }
}
