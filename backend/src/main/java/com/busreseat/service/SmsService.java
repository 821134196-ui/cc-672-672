package com.busreseat.service;

import com.busreseat.api.Dtos;
import com.busreseat.model.Assignment;
import com.busreseat.model.AssignmentStatus;
import com.busreseat.model.Booking;
import com.busreseat.model.SmsMessage;
import com.busreseat.model.Trip;
import com.busreseat.model.Vehicle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

/** 短信接口本地模拟：只落库不真正外发，可通过 /api/mock/sms 查看 */
@ApplicationScoped
public class SmsService {

    @Transactional
    public void send(String phone, String content) {
        SmsMessage m = new SmsMessage();
        m.phone = phone;
        m.content = content;
        m.persist();
    }

    /** 换车后通知乘客新座位（在重排事务内调用，同事务提交） */
    public void notifyReassigned(Booking b, Assignment a, Trip trip, Vehicle vehicle) {
        String content;
        if (a.status == AssignmentStatus.MANUAL_REVIEW) {
            content = "【客运模拟】" + b.passengerName + "您好，" + trip.code + " 次已更换车辆（"
                    + vehicle.plateNumber + "），您的特殊座位需求暂时无法满足，工作人员将尽快与您联系。";
        } else {
            content = "【客运模拟】" + b.passengerName + "您好，" + trip.code + " 次已更换车辆（"
                    + vehicle.plateNumber + "），您的座位由 " + b.originalSeatNo + " 调整为 " + a.seatNo
                    + "，请尽快在乘客页面确认。";
        }
        send(b.phone, content);
    }

    public List<Dtos.SmsView> listAll() {
        return SmsMessage.<SmsMessage>list("order by id desc").stream()
                .map(m -> new Dtos.SmsView(m.id, m.phone, m.content, m.createdAt))
                .collect(Collectors.toList());
    }
}
