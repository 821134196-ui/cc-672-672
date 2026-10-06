package com.busreseat.api;

import com.busreseat.service.DispatchMockService;
import com.busreseat.service.ReassignmentService;
import com.busreseat.service.TripService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.List;

/** 调度台接口 */
@Path("/api/dispatcher")
public class DispatcherResource {

    @Inject
    TripService tripService;

    @Inject
    ReassignmentService reassignmentService;

    @Inject
    DispatchMockService dispatchMockService;

    @GET
    @Path("/trips")
    public List<Dtos.TripSummary> trips() {
        return tripService.listTrips();
    }

    @GET
    @Path("/trips/{tripId}")
    public Dtos.TripDetail trip(@PathParam("tripId") long tripId) {
        return tripService.tripDetail(tripId);
    }

    @GET
    @Path("/trips/{tripId}/versions")
    public List<Dtos.VersionView> versions(@PathParam("tripId") long tripId) {
        return tripService.versions(tripId);
    }

    /** 车辆调度模拟接口：可用备用车 */
    @GET
    @Path("/trips/{tripId}/available-vehicles")
    public List<Dtos.VehicleOption> availableVehicles(@PathParam("tripId") long tripId) {
        return dispatchMockService.availableForTrip(tripId);
    }

    public record ChangeVehicleRequest(long vehicleId, String operator) {
    }

    /** 换车重排：两名调度并发操作时，乐观锁保证只有一个成功 */
    @POST
    @Path("/trips/{tripId}/change-vehicle")
    public Dtos.ReassignResult changeVehicle(@PathParam("tripId") long tripId, ChangeVehicleRequest req) {
        return reassignmentService.changeVehicle(tripId, req.vehicleId(),
                req.operator() == null || req.operator().isBlank() ? "调度员" : req.operator());
    }

    @GET
    @Path("/versions/{versionId}/seats")
    public Dtos.SeatMapView seats(@PathParam("versionId") long versionId) {
        return tripService.seatMap(versionId);
    }

    public record ManualAssignRequest(long bookingId, String seatNo) {
    }

    /** 手动调座：唯一约束保证并发下同一座位不会分给两个人 */
    @POST
    @Path("/versions/{versionId}/assign")
    public Dtos.BookingView manualAssign(@PathParam("versionId") long versionId, ManualAssignRequest req) {
        return reassignmentService.manualAssign(versionId, req.bookingId(), req.seatNo());
    }
}
