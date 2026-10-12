package com.dmp.riskdecision.component.liteflow.service;

import com.dmp.riskdecision.component.liteflow.registry.ComponentRegistry;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** 对业务模块提供可编排组件的查找能力。 */
@Service
public class ComponentCatalogService {
    @Resource
    private ComponentRegistry registry;

    public ComponentCatalogService() {
    }

    public ComponentCatalogService(ComponentRegistry registry) {
        this.registry = registry;
    }

    public ComponentDefinition require(String componentKey) {
        return registry.require(componentKey);
    }
}
