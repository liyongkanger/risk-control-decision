package com.dmp.riskdecision.policy.mapper;

import lombok.Data;

/** 流程草稿查询对应的数据库行。 */
@Data
public class FlowDraftRow {
    private Long flowId;
    private String graphJson;
    private long revision;
}
