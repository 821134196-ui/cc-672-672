package com.busreseat.model;

/** 座位分配状态：待乘客确认 / 乘客已确认 / 需求无法满足转人工 */
public enum AssignmentStatus {
    PENDING,
    CONFIRMED,
    MANUAL_REVIEW
}
