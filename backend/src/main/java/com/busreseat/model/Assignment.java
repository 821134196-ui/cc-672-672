package com.busreseat.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 某个座位版本里，一名乘客分到的座位。
 * (version_id, seatNo) 唯一：两名调度并发操作时，数据库保证同一座位不会分给两个人。
 */
@Entity
@Table(name = "assignment", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"version_id", "seatNo"}),
        @UniqueConstraint(columnNames = {"version_id", "booking_id"})
})
public class Assignment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    public SeatVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    public Booking booking;

    /** 转人工时可能为 null（没有可满足需求的座位） */
    public String seatNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AssignmentStatus status = AssignmentStatus.PENDING;

    /** 同行人是否被拆散（调度预警） */
    @Column(nullable = false)
    public boolean groupSplit = false;

    /** 说明，如"新车无轮椅位，转人工" */
    public String note;
}
