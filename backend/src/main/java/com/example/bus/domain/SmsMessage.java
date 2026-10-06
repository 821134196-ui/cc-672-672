package com.example.bus.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 模拟短信：重排后“发送”给乘客的座位变更通知（本地落库 + 写日志文件） */
@Entity
@Table(name = "sms_message")
public class SmsMessage extends BaseEntity {

    @Column(name = "passenger_id")
    public Long passengerId;

    public String phone;

    @Column(name = "version_no")
    public int versionNo;

    @Column(length = 500)
    public String content;

    @Column(name = "sent_at")
    public Instant sentAt;
}
