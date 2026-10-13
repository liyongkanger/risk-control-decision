package com.dmp.riskdecision.policy.service;

/** 创建流程时流程码已经存在。 */
public class FlowCodeConflictException extends RuntimeException {
    public FlowCodeConflictException(String flowCode, Throwable cause) {
        super("Flow code already exists: " + flowCode, cause);
    }
}
