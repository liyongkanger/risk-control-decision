package com.dmp.riskdecision.policy.mapper;

import lombok.Data;

/** 已保存流程版本对应的数据库行。 */
@Data
public class FlowVersionRow {
    private long id;
    private Long flowId;
    private String flowCode;
    private long versionNumber;
    private String graphJson;
    private String chainId;
    private String compiledEl;
}
