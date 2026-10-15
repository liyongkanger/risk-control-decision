package com.dmp.riskdecision.component.variable.vo.resp;

import com.dmp.riskdecision.component.variable.model.VariablePage;
import java.util.List;
import lombok.Getter;

/** 变量中心后台分页响应。 */
@Getter
public class VariablePageResp {
    private final List<VariableResp> records;
    private final long total;
    private final int page;
    private final int pageSize;

    public VariablePageResp(VariablePage result) {
        records = result.getRecords().stream().map(VariableResp::new).toList();
        total = result.getTotal();
        page = result.getPage();
        pageSize = result.getPageSize();
    }
}
