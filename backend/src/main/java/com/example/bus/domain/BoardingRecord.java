package com.example.bus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 检票（登车）记录。唯一键为（乘客, 座位版本）：
 * 连续换车会产生新版本，但旧版本的检票记录原样保留，形成完整检票历史；
 * 乘客在新车上确认新座位后可产生新版本的检票记录。
 */
@Entity
@Table(name = "boarding_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_boarding_passenger_version",
                columnNames = {"passenger_id", "version_no"}))
public class BoardingRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id")
    public Passenger passenger;

    @Column(name = "boarded_at", nullable = false)
    public Instant boardedAt;

    /** 检票操作人（司机） */
    @Column(name = "boarded_by")
    public String boardedBy;

    /** 检票时的座位版本号 */
    @Column(name = "version_no", nullable = false)
    public int versionNo;
}
