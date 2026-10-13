package com.dmp.riskdecision.policy.vo.req;

import lombok.Data;

/** 新建场景请求。 */
@Data
public class CreateSceneReq {
    private String sceneCode;
    private String sceneName;
    private String description;
}
