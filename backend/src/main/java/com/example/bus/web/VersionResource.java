package com.example.bus.web;

import com.example.bus.domain.SeatVersion;
import com.example.bus.domain.VersionStatus;
import com.example.bus.service.BusinessConflictException;
import com.example.bus.service.SeatService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 座位版本历史：连续换车后可回看每一个版本的名单与检票记录 */
@Path("/api/versions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VersionResource {

    @Inject
    SeatService seatService;

    @GET
    public List<Map<String, Object>> list() {
        return SeatVersion.<SeatVersion>findAll().list().stream()
                .sorted((a, b) -> b.versionNo - a.versionNo)
                .map(v -> Map.<String, Object>of(
                        "versionNo", v.versionNo,
                        "status", v.status.name(),
                        "vehicleCode", v.vehicle.code,
                        "vehicleName", v.vehicle.name,
                        "reason", v.reason == null ? "" : v.reason,
                        "createdAt", v.createdAt.toString()
                ))
                .toList();
    }

    /** 某一历史版本的名单快照（含当时的确认/检票状态） */
    @GET
    @Path("/{no}")
    public Map<String, Object> detail(@PathParam("no") int no) {
        return seatService.versionDetail(no);
    }
}
