package com.busreseat.api;

import com.busreseat.service.TripService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/** 乘客接口：并列查看原座/新座并确认 */
@Path("/api/passenger")
public class PassengerResource {

    @Inject
    TripService tripService;

    @GET
    @Path("/bookings/{bookingId}")
    public Dtos.PassengerView view(@PathParam("bookingId") long bookingId) {
        return tripService.passengerView(bookingId);
    }

    @POST
    @Path("/bookings/{bookingId}/confirm")
    public Dtos.PassengerView confirm(@PathParam("bookingId") long bookingId) {
        return tripService.confirm(bookingId);
    }
}
