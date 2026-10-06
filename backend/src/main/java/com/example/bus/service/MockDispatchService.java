package com.example.bus.service;

import com.example.bus.domain.DispatchLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 本地模拟车辆调度接口：真实系统中此处调用车队调度平台确认调车。
 * 本地实现只把换车工单落库（/api/dispatch-logs 可查）。
 */
@ApplicationScoped
public class MockDispatchService {

    @Transactional
    public DispatchLog logSwap(String fromVehicle, String toVehicle, int versionNo, String operator, String note) {
        DispatchLog log = new DispatchLog();
        log.fromVehicle = fromVehicle;
        log.toVehicle = toVehicle;
        log.versionNo = versionNo;
        log.operator = operator;
        log.note = note;
        log.createdAt = Instant.now();
        log.persist();
        return log;
    }

    public List<DispatchLog> recent() {
        return DispatchLog.find("order by id desc").page(0, 100).list();
    }
}
