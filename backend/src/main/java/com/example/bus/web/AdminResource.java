package com.example.bus.web;

import com.example.bus.service.DataSeeder;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/** 演示辅助：清空并重建种子数据 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    DataSeeder seeder;

    @POST
    @Path("/reset")
    public Map<String, Object> reset() {
        seeder.reset();
        return Map.of("reset", true);
    }
}
