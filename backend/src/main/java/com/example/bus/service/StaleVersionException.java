package com.example.bus.service;

/**
 * 座位版本过期/冲突：前端所持版本号已不是最新版本（两名调度并发操作时，
 * 后提交者必须刷新后再决定），映射为 HTTP 409。
 */
public class StaleVersionException extends BusinessException {
    public StaleVersionException(String message) {
        super(message);
    }
}
