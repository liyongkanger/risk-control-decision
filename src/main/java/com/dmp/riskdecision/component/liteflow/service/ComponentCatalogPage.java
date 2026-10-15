package com.dmp.riskdecision.component.liteflow.service;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 代码注册组件目录的分页查询结果。 */
@Data
@AllArgsConstructor
public class ComponentCatalogPage {
    private List<ComponentDefinition> records;
    private long total;
    private int page;
    private int pageSize;
}
