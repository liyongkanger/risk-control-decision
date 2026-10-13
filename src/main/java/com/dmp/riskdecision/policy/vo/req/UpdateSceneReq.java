package com.dmp.riskdecision.policy.vo.req;

import lombok.Data;

/** 编辑场景名称与描述的请求。 */
@Data
public class UpdateSceneReq {
    private String sceneName;
    private String description;
}
