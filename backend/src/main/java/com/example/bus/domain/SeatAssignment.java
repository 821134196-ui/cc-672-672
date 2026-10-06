package com.example.bus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 座位分配：某个座位版本下，乘客与座位的一一对应。
 * 新建时为 PROPOSED（待乘客确认），乘客确认后为 CONFIRMED。
 *
 * 数据库层唯一约束保证同一版本中：
 *   - 一个座位最多分给一个乘客
 *   - 一个乘客最多占一个座位
 */
@Entity
@Table(name = "seat_assignment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_assignment_version_seat",
                        columnNames = {"version_id", "seat_code"}),
                @UniqueConstraint(name = "uk_assignment_version_passenger",
                        columnNames = {"version_id", "passenger_id"})
        })
public class SeatAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    public SeatVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id")
    public Passenger passenger;

    @Column(name = "seat_code", nullable = false)
    public String seatCode;

    /** 座位类型快照，便于名单展示 */
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false)
    public SeatType seatType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AssignmentStatus status = AssignmentStatus.PROPOSED;

    @Column(name = "confirmed_at")
    public Instant confirmedAt;
}
