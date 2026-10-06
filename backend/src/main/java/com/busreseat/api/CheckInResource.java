package com.busreseat.api;

import com.busreseat.service.TripService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.List;

/** 检票接口：未确认新座位的乘客不能记为已登车 */
@Path("/api/checkins")
public class CheckInResource {

    @Inject
    TripService tripService;

    public record CheckInRequest(long bookingId) {
    }

    @POST
    public Dtos.CheckInView checkIn(CheckInRequest req) {
        return tripService.checkIn(req.bookingId());
    }

    @GET
    @Path("/trip/{tripId}")
    public List<Dtos.CheckInView> ofTrip(@PathParam("tripId") long tripId) {
        return tripService.checkInsOfTrip(tripId);
    }
}
