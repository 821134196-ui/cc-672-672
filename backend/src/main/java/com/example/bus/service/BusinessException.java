package com.example.bus.service;

/** 业务异常基类，统一映射为 HTTP 409 */
public abstract class BusinessException extends RuntimeException {
    protected BusinessException(String message) {
        super(message);
    }
}
