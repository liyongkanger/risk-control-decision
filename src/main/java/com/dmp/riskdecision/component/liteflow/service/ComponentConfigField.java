package com.dmp.riskdecision.component.liteflow.service;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 后台可编辑组件配置的字段描述。 */
@Data
@AllArgsConstructor
public class ComponentConfigField {
    private String name;
    private String label;
    private String type;
    private boolean required;
}
