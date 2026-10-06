package com.example.bus.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * 实体基类：使用 IDENTITY 自增主键（SQLite INTEGER PRIMARY KEY）。
 * 不用 PanacheEntity 默认的序列表生成器——后者在分配 ID 时要新开连接，
 * 而本系统连接池固定为 1 个连接（写事务串行化），会导致取号超时。
 * IDENTITY 通过 last_insert_rowid 在同一连接内取回 id，无此问题。
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
}
