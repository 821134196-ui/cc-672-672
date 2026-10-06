package com.example.bus.model;

import com.example.bus.domain.SeatType;

/** 座位图中的单个座位定义（座位图 JSON 的反序列化对象） */
public class SeatDef {
    public String code;
    public int row;
    public String col;
    /** 同一排内从左到右的顺序号（相邻座位 order 相差 1） */
    public int order;
    public SeatType type;

    public SeatDef() {}

    public SeatDef(String code, int row, String col, int order, SeatType type) {
        this.code = code;
        this.row = row;
        this.col = col;
        this.order = order;
        this.type = type;
    }
}
