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
import java.time.LocalDateTime;

/** 一次换车产生一个座位版本；再次换车时旧版本保留为 SUPERSEDED */
@Entity
@Table(name = "seat_version", uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "versionNo"}))
public class SeatVersion extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    public Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id")
    public Vehicle vehicle;

    @Column(nullable = false)
    public int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public VersionStatus status = VersionStatus.ACTIVE;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    /** 操作人（调度员） */
    public String operator;
}
