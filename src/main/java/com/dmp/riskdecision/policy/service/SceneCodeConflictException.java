package com.dmp.riskdecision.policy.service;

/** 创建场景时场景码已经存在。 */
public class SceneCodeConflictException extends RuntimeException {
    public SceneCodeConflictException(String sceneCode, Throwable cause) {
        super("Scene code already exists: " + sceneCode, cause);
    }
}
