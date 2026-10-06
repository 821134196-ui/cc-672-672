package com.example.bus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 模拟车辆调度接口的换车工单记录 */
@Entity
@Table(name = "dispatch_log")
public class DispatchLog extends BaseEntity {

    @Column(name = "from_vehicle")
    public String fromVehicle;

    @Column(name = "to_vehicle", nullable = false)
    public String toVehicle;

    @Column(name = "version_no")
    public Integer versionNo;

    public String operator;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public String note;
}
