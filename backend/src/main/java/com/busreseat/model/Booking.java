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

/** 已售订单（乘客）。groupId 相同的为同行人 */
@Entity
@Table(name = "booking")
public class Booking extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    public Trip trip;

    @Column(nullable = false)
    public String passengerName;

    @Column(nullable = false)
    public String phone;

    /** 同行人分组号，null 表示单人 */
    public String groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SpecialNeed need = SpecialNeed.NONE;

    /** 原车上的座位号（换车前已售出的座位） */
    @Column(nullable = false)
    public String originalSeatNo;
}
