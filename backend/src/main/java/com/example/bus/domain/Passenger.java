package com.example.bus.domain;

import jakarta.persistence.*;

/**
 * 乘客（已售票）。持有原座位（最初车型上的座位号）、特殊需求、同行组。
 */
@Entity
@Table(name = "passenger")
public class Passenger extends BaseEntity {

    @Column(nullable = false)
    public String name;

    public String phone;

    /** 原车型上的座位编号（票面座位，永不变更，供乘客页“原座”展示） */
    @Column(name = "original_seat", nullable = false)
    public String originalSeat;

    @Enumerated(EnumType.STRING)
    @Column(name = "special_need", nullable = false)
    public SpecialNeed specialNeed = SpecialNeed.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    public TravelGroup group;

    public static Passenger findByName(String name) {
        return find("name", name).firstResult();
    }
}
