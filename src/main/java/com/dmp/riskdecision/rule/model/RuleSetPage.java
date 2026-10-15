package com.dmp.riskdecision.rule.model;

import java.util.List;
import lombok.Getter;

/** 规则集后台列表的服务层分页结果。 */
@Getter
public class RuleSetPage {
    private final List<RuleSetSummary> records;
    private final long total;
    private final int page;
    private final int pageSize;

    public RuleSetPage(List<RuleSetSummary> records, long total, int page, int pageSize) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
