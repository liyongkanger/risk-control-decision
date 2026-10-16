package com.dmp.riskdecision.decision.vo.resp;

import java.util.List;
import lombok.Data;

/** 决策记录列表的标准分页响应。 */
@Data
public class DecisionRecordPageResp {
    private List<DecisionRecordResp> records;
    private long total;
    private int page;
    private int pageSize;
}
