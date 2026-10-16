package com.dmp.riskdecision.decision.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 决策执行记录的数据库分页结果。 */
@Data
@AllArgsConstructor
public class DecisionRecordPage {
    private List<DecisionRecord> records;
    private long total;
    private int page;
    private int pageSize;
}
