package com.example.bus.domain;

import jakarta.persistence.*;

/**
 * 换车重排产生的问题：特殊需求无法满足（转人工）或同行人被拆散（调度可见）。
 */
@Entity
@Table(name = "reassignment_issue")
public class ReassignmentIssue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    public SeatVersion version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IssueType type;

    /** 受影响乘客（OVERFLOW 时可空） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id")
    public Passenger passenger;

    /** 受影响同行组（GROUP_SPLIT 时填写） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    public TravelGroup group;

    @Column(nullable = false)
    public String detail;

    /** 调度处理状态：OPEN -> RESOLVED/ESCALATED */
    @Column(nullable = false)
    public String state = "OPEN";
}
