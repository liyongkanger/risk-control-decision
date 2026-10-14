package com.dmp.riskdecision.policy.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 独立流程资产的分页查询结果。 */
@Getter
@AllArgsConstructor
public class FlowPage {
    private final List<FlowInfo> records;
    private final long total;
    private final int page;
    private final int pageSize;
}
