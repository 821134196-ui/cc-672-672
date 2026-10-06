package com.example.bus.web;

import com.example.bus.domain.Passenger;
import com.example.bus.service.SeatService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 乘客列表与乘客个人视图 */
@Path("/api/passengers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PassengerResource {

    @Inject
    SeatService seatService;

    @GET
    public List<Map<String, Object>> list() {
        return Passenger.<Passenger>findAll().list().stream().map(p -> {
            var g = p.group;
            return Map.<String, Object>of(
                    "id", p.id,
                    "name", p.name,
                    "phone", p.phone == null ? "" : p.phone,
                    "originalSeat", p.originalSeat,
                    "specialNeed", p.specialNeed.name(),
                    "groupId", g == null ? -1 : g.id,
                    "groupName", g == null ? "" : g.name
            );
        }).toList();
    }

    /** 乘客页：原座/新座并列、确认状态、检票状态 */
    @GET
    @Path("/{id}")
    public Map<String, Object> view(@PathParam("id") Long id) {
        return seatService.passengerView(id);
    }

    public record ConfirmRequest(Integer expectedVersionNo) {}

    /** 乘客确认新座位 */
    @POST
    @Path("/{id}/confirm")
    public Map<String, Object> confirm(@PathParam("id") Long id, ConfirmRequest req) {
        Integer v = req == null ? null : req.expectedVersionNo();
        return seatService.confirm(id, v);
    }
}
