package com.busreseat.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 短信发送记录（本地模拟，不真正外发） */
@Entity
@Table(name = "sms_message")
public class SmsMessage extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String phone;

    @Column(nullable = false, length = 500)
    public String content;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
