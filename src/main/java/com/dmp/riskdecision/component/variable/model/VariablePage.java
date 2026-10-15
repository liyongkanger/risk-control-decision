package com.dmp.riskdecision.component.variable.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 变量中心后台分页结果。 */
@Data
@AllArgsConstructor
public class VariablePage {
    private List<VariableVersion> records;
    private long total;
    private int page;
    private int pageSize;
}
