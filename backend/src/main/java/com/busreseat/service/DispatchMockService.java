package com.busreseat.service;

import com.busreseat.api.Dtos;
import com.busreseat.model.Seat;
import com.busreseat.model.Trip;
import com.busreseat.model.Vehicle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.stream.Collectors;

/** 车辆调度接口本地模拟：返回可调配的备用车辆（排除当前在用车） */
@ApplicationScoped
public class DispatchMockService {

    public List<Dtos.VehicleOption> availableForTrip(long tripId) {
        Trip trip = Trip.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("班次不存在: " + tripId);
        }
        List<Vehicle> vehicles = Vehicle.list("dispatchable = true and id <> ?1 order by id", trip.activeVehicle.id);
        return vehicles.stream()
                .map(v -> Dtos.vehicleOption(v, Seat.list("vehicle.id", v.id)))
                .collect(Collectors.toList());
    }
}
