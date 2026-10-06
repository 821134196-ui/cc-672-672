package com.example.bus.service;

import com.example.bus.domain.SmsMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 本地模拟短信接口：把座位变更通知落库（/api/sms 可查），
 * 同时追加写入本地日志文件（模拟真实短信网关的出站记录）。
 */
@ApplicationScoped
public class MockSmsService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @ConfigProperty(name = "app.sms.mock-file")
    String mockFile;

    @Transactional
    public SmsMessage send(Long passengerId, String phone, int versionNo, String content) {
        SmsMessage msg = new SmsMessage();
        msg.passengerId = passengerId;
        msg.phone = phone;
        msg.versionNo = versionNo;
        msg.content = content;
        msg.sentAt = Instant.now();
        msg.persist();

        try {
            Path path = Paths.get(mockFile);
            Files.createDirectories(path.getParent());
            String line = String.format("[%s] -> %s (v%d, passenger#%d): %s%n",
                    FMT.format(msg.sentAt), phone == null ? "-" : phone, versionNo, passengerId, content);
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 模拟网关文件写入失败不影响主流程
            System.getLogger("sms").log(System.Logger.Level.WARNING, "短信模拟文件写入失败: " + e.getMessage());
        }
        return msg;
    }

    public List<SmsMessage> recent() {
        return SmsMessage.find("order by id desc").page(0, 100).list();
    }
}
