package com.dmp.riskdecision.component.liteflow.vo.resp;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 只读组件目录的后台分页响应。 */
@Data
@AllArgsConstructor
public class ComponentPageResp {
    private List<ComponentDefinitionResp> records;
    private long total;
    private int page;
    private int pageSize;
}
