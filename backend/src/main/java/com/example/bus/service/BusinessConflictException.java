package com.example.bus.service;

/** 业务冲突（未确认即检票、无座位、重复操作等），映射为 HTTP 409 */
public class BusinessConflictException extends BusinessException {
    public BusinessConflictException(String message) {
        super(message);
    }
}
