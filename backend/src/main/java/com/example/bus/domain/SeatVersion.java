package com.example.bus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 座位版本。每执行一次换车重排生成一个新版本；
 * 旧版本置为 SUPERSEDED 但记录保留，形成完整的座位版本历史。
 */
@Entity
@Table(name = "seat_version")
public class SeatVersion extends BaseEntity {

    /** 本版本对应的（新）车辆 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    public Vehicle vehicle;

    /** 版本号，从 1 递增 */
    @Column(name = "version_no", nullable = false)
    public int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public VersionStatus status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** 换车说明，例如“临时更换为车型 B-45(45座)” */
    public String reason;

    /**
     * 活动版本守卫：ACTIVE 版本写 1，SUPERSEDED 写 null。
     * SQLite（以及大多数数据库）唯一约束允许多个 NULL，
     * 因此数据库层面保证任意时刻至多一个活动版本——两名调度并发换车时，
     * 后提交的事务会因唯一冲突失败，而不会产生两个并行“最终名单”。
     */
    @Column(name = "active_flag", unique = true)
    public Integer activeFlag;

    public static SeatVersion findActive() {
        return find("status", VersionStatus.ACTIVE).firstResult();
    }
}
