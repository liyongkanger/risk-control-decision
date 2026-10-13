package com.dmp.riskdecision.policy.vo.resp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 场景基本信息与当前发布版本响应。 */
@Data
@AllArgsConstructor
public class SceneResp {
    private String sceneCode;
    private String sceneName;
    private String flowCode;
    private String description;
    private Long publishedVersionNumber;
    private LocalDateTime updatetime;
}
