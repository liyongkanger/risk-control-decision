package com.dmp.riskdecision.rule.vo.resp;

import com.dmp.riskdecision.rule.model.RuleSetPage;
import java.util.List;
import lombok.Getter;

/** 规则集后台列表的分页响应。 */
@Getter
public class RuleSetPageResp {
    private final List<RuleSetSummaryResp> records;
    private final long total;
    private final int page;
    private final int pageSize;

    public RuleSetPageResp(RuleSetPage source) {
        records = source.getRecords().stream().map(RuleSetSummaryResp::new).toList();
        total = source.getTotal();
        page = source.getPage();
        pageSize = source.getPageSize();
    }
}
