package com.busreseat.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 车辆（车型决定座位图） */
@Entity
@Table(name = "vehicle")
public class Vehicle extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String plateNumber;

    @Column(nullable = false)
    public String model;

    /** 是否可被调度（车辆调度模拟接口只返回可用车） */
    @Column(nullable = false)
    public boolean dispatchable = true;
}
