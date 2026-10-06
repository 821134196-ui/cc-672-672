package com.example.bus.domain;

import jakarta.persistence.*;

/** 同行人分组（家庭、朋友等，换车后应尽量安排相邻座位） */
@Entity
@Table(name = "travel_group")
public class TravelGroup extends BaseEntity {

    @Column(unique = true, nullable = false)
    public String name;
}
