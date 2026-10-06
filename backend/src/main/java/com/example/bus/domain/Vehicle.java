package com.example.bus.domain;

import jakarta.persistence.*;

/**
 * 车辆。seatMap 为座位图 JSON：
 * [{"code":"1A","row":1,"col":"A","type":"STANDARD"}, ...]
 */
@Entity
@Table(name = "vehicle")
public class Vehicle extends BaseEntity {

    @Column(unique = true, nullable = false)
    public String code;

    @Column(nullable = false)
    public String name;

    /** 座位图 JSON 字符串 */
    @Column(name = "seat_map", nullable = false)
    public String seatMap;

    /** 是否在本地模拟的“车辆调度接口”中可调配 */
    @Column(name = "available")
    public boolean available = true;

    public static Vehicle findByCode(String code) {
        return find("code", code).firstResult();
    }
}
