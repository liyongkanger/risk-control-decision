package com.dmp.riskdecision.policy.vo.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 流程发布响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishFlowResp {
    private long id;
    private String sceneCode;
    private long versionNumber;
    private String chainId;

}
