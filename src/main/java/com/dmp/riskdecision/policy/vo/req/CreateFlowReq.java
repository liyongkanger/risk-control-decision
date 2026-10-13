package com.dmp.riskdecision.policy.vo.req;

import lombok.Data;

/** 创建独立流程资产的请求。 */
@Data
public class CreateFlowReq {
    private String flowCode;
    private String flowName;
}
