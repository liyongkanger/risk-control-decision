package com.dmp.riskdecision.orchestrator;

import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.flow.FlowBus;
import org.springframework.stereotype.Component;

@Component
public class LiteFlowChainRegistry {
    public void load(String chainId, String el) {
        if (FlowBus.containChain(chainId)) return;
        LiteFlowChainELBuilder.createChain().setChainName(chainId).setEL(el).build();
    }

    public boolean contains(String chainId) {
        return FlowBus.containChain(chainId);
    }
}
