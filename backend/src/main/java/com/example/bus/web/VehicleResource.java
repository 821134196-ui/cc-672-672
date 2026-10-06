package com.example.bus.web;

import com.example.bus.domain.Vehicle;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 车辆（模拟车辆调度接口的可调配车辆列表） */
@Path("/api/vehicles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VehicleResource {

    @GET
    public List<Map<String, Object>> list(@QueryParam("all") boolean all) {
        List<Vehicle> vehicles = all
                ? Vehicle.findAll().list()
                : Vehicle.list("available", true);
        return vehicles.stream().map(v -> {
            int total = v.seatMap.split("\"code\"").length - 1;
            return Map.<String, Object>of(
                    "code", v.code,
                    "name", v.name,
                    "available", v.available,
                    "seatCount", total
            );
        }).toList();
    }
}
