package com.busreseat.api;

import com.busreseat.service.SmsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;

/** 本地模拟接口的查看入口 */
@Path("/api/mock")
public class MockResource {

    @Inject
    SmsService smsService;

    /** 短信模拟：查看所有"已发送"短信 */
    @GET
    @Path("/sms")
    public List<Dtos.SmsView> sms() {
        return smsService.listAll();
    }
}
