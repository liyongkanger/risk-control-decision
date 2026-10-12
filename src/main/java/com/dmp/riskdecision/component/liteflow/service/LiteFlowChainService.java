package com.dmp.riskdecision.component.liteflow.service;

import com.dmp.riskdecision.component.liteflow.registry.LiteFlowChainRegistry;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** 对业务模块提供流程链的加载和存在性检查。 */
@Service
public class LiteFlowChainService {
    @Resource
    private LiteFlowChainRegistry registry;

    public LiteFlowChainService() {
    }

    public LiteFlowChainService(LiteFlowChainRegistry registry) {
        this.registry = registry;
    }

    public void load(String chainId, String el) {
        registry.load(chainId, el);
    }

    public boolean contains(String chainId) {
        return registry.contains(chainId);
    }
}
