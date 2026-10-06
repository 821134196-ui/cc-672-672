package com.example.bus.web;

import com.example.bus.service.SeatService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/** 司机检票：未确认新座位的乘客会被拒绝 */
@Path("/api/boarding")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BoardingResource {

    @Inject
    SeatService seatService;

    public static class BoardRequest {
        public String operator;
    }

    @POST
    @Path("/{passengerId}/board")
    public Map<String, Object> board(@PathParam("passengerId") Long passengerId, BoardRequest req) {
        return seatService.board(passengerId, req == null ? null : req.operator);
    }

    @POST
    @Path("/{passengerId}/unboard")
    public Map<String, Object> unboard(@PathParam("passengerId") Long passengerId) {
        return seatService.unboard(passengerId);
    }
}
