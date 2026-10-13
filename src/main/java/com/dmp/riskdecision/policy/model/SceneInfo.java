package com.dmp.riskdecision.policy.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 场景基本信息及当前发布版本。 */
@Getter
@AllArgsConstructor
public class SceneInfo {
    private final String sceneCode;
    private final String sceneName;
    private final String flowCode;
    private final String description;
    private final Long publishedVersionNumber;
    private final LocalDateTime updatetime;
}
