package com.busreseat.api;

import com.busreseat.service.TripService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/** 司机接口：只能拿到与乘客确认一致的最终名单 */
@Path("/api/driver")
public class DriverResource {

    @Inject
    TripService tripService;

    @GET
    @Path("/trips/{tripId}/manifest")
    public Dtos.ManifestView manifest(@PathParam("tripId") long tripId) {
        return tripService.manifest(tripId);
    }
}
