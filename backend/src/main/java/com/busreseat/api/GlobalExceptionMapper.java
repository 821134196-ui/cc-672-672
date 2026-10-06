package com.busreseat.api;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 统一异常映射：
 * - 乐观锁冲突 / 唯一约束冲突（并发换车、并发占座）→ 409
 * - WebApplicationException 原样透传
 * - 其余 → 500
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof WebApplicationException wae) {
            return json(wae.getResponse().getStatus(), message(e));
        }
        Throwable conflict = findConflict(e);
        if (conflict != null) {
            return json(409, "操作冲突：数据已被其他调度同时修改，请刷新后重试");
        }
        return json(500, "服务器内部错误: " + e.getMessage());
    }

    private static Throwable findConflict(Throwable t) {
        while (t != null) {
            if (t instanceof OptimisticLockException
                    || t instanceof jakarta.persistence.PersistenceException
                    || t.getClass().getName().contains("ConstraintViolation")) {
                return t;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("UNIQUE constraint") || msg.contains("optimistic"))) {
                return t;
            }
            t = t.getCause();
        }
        return null;
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? "请求失败" : e.getMessage();
    }

    private static Response json(int status, String message) {
        return Response.status(status)
                .entity(new Dtos.ErrorView(message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
