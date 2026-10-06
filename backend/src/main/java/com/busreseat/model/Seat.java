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

/** 某辆车上的一个物理座位 */
@Entity
@Table(name = "seat", uniqueConstraints = @UniqueConstraint(columnNames = {"vehicle_id", "seatNo"}))
public class Seat extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id")
    public Vehicle vehicle;

    /** 座位号，如 3A */
    @Column(nullable = false)
    public String seatNo;

    @Column(nullable = false)
    public int rowIdx;

    /** 列号，用于判断"相邻"：同一排内列号连续即为邻座 */
    @Column(nullable = false)
    public int colIdx;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SeatKind kind = SeatKind.NORMAL;
}
