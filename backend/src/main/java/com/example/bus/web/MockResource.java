package com.example.bus.web;

import com.example.bus.service.MockDispatchService;
import com.example.bus.service.MockSmsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 本地模拟外部接口的出站记录：短信通知、车辆调度工单 */
@Path("/api/mock")
@Produces(MediaType.APPLICATION_JSON)
public class MockResource {

    @Inject
    MockSmsService smsService;

    @Inject
    MockDispatchService dispatchService;

    @GET
    @Path("/sms")
    public List<?> sms() {
        return smsService.recent().stream().map(m -> Map.of(
                "id", m.id,
                "phone", m.phone == null ? "" : m.phone,
                "versionNo", m.versionNo,
                "content", m.content == null ? "" : m.content,
                "sentAt", m.sentAt == null ? "" : m.sentAt.toString()
        )).toList();
    }

    @GET
    @Path("/dispatch-logs")
    public List<?> dispatchLogs() {
        return dispatchService.recent().stream().map(l -> Map.of(
                "id", l.id,
                "fromVehicle", l.fromVehicle == null ? "" : l.fromVehicle,
                "toVehicle", l.toVehicle,
                "versionNo", l.versionNo == null ? 0 : l.versionNo,
                "operator", l.operator == null ? "" : l.operator,
                "note", l.note == null ? "" : l.note,
                "createdAt", l.createdAt == null ? "" : l.createdAt.toString()
        )).toList();
    }
}
