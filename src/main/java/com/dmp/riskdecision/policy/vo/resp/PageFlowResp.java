package com.dmp.riskdecision.policy.vo.resp;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 独立流程资产的分页响应。 */
@Data
@AllArgsConstructor
public class PageFlowResp {
    private List<FlowInfoResp> records;
    private long total;
    private int page;
    private int pageSize;
}
