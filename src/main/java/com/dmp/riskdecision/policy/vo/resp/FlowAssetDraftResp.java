package com.dmp.riskdecision.policy.vo.resp;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 独立流程资产的草稿与修订号。 */
@Data
@AllArgsConstructor
public class FlowAssetDraftResp {
    private String flowCode;
    private GraphDefinition graph;
    private long revision;
}
