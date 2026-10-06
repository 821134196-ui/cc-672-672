package com.example.bus.web;

import com.example.bus.service.SeatService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * 座位与换车操作。
 * POST /api/seat/swap  调度选择新车触发重排（携带 expectedVersionNo 做并发守卫）
 * GET  /api/manifest   当前活动版本完整名单（乘客端与司机端共用同一份）
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SeatResource {

    @Inject
    SeatService seatService;

    public static class SwapRequest {
        public String vehicleCode;
        public Integer expectedVersionNo;
        public String operator;
        public String reason;
    }

    @POST
    @Path("/seat/swap")
    public Map<String, Object> swap(SwapRequest req) {
        if (req == null || req.vehicleCode == null || req.vehicleCode.isBlank()) {
            throw new BadRequestException("vehicleCode 不能为空");
        }
        return seatService.swapVehicle(
                req.operator == null || req.operator.isBlank() ? "dispatcher" : req.operator,
                req.vehicleCode, req.expectedVersionNo, req.reason);
    }

    @GET
    @Path("/manifest")
    public Map<String, Object> manifest() {
        return seatService.manifest();
    }
}
