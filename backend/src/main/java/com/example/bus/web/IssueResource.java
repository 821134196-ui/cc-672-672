package com.example.bus.web;

import com.example.bus.domain.ReassignmentIssue;
import com.example.bus.service.SeatService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 换车问题列表：特殊需求未满足（转人工）、同行人拆散、超载 */
@Path("/api/issues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IssueResource {

    @Inject
    SeatService seatService;

    @GET
    public List<Map<String, Object>> list(@QueryParam("state") String state) {
        var query = state == null || state.isBlank()
                ? ReassignmentIssue.findAll()
                : ReassignmentIssue.find("state", state);
        return query.<ReassignmentIssue>list().stream().map(i -> Map.<String, Object>of(
                "id", i.id,
                "versionNo", i.version.versionNo,
                "type", i.type.name(),
                "state", i.state,
                "detail", i.detail
        )).toList();
    }

    public static class IssueStateRequest {
        public String state;
    }

    /** 调度处理问题：RESOLVED 已解决 / ESCALATED 已转人工 */
    @POST
    @Path("/{id}/state")
    public Map<String, Object> updateState(@PathParam("id") Long id, IssueStateRequest req) {
        if (req == null || req.state == null) throw new BadRequestException("state 不能为空");
        return seatService.updateIssueState(id, req.state);
    }
}
