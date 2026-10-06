package com.busreseat.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 客运班次。activeVersion 指向当前生效的座位版本；乐观锁防止两名调度同时换车 */
@Entity
@Table(name = "trip")
public class Trip extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 乐观锁：两名调度并发换车时，后提交者失败 */
    @Version
    public long lockVersion;

    @Column(nullable = false, unique = true)
    public String code;

    @Column(nullable = false)
    public String route;

    @Column(nullable = false)
    public String departureTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_vehicle_id")
    public Vehicle baseVehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "active_vehicle_id")
    public Vehicle activeVehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_version_id")
    public SeatVersion activeVersion;
}
