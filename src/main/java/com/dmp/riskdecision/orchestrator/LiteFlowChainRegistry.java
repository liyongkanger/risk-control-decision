package com.dmp.riskdecision.orchestrator;

import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.flow.FlowBus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class LiteFlowChainRegistry {
    private final Map<String, String> loadedEl = new HashMap<>();

    public synchronized void load(String chainId, String el) {
        String existing = loadedEl.get(chainId);
        if (existing != null && !existing.equals(el)) {
            throw new IllegalStateException("Chain ID already has different EL: " + chainId);
        }
        if (FlowBus.containChain(chainId)) {
            if (existing == null) {
                String activeEl = FlowBus.getChain(chainId).getEl();
                if (!el.equals(activeEl)) {
                    throw new IllegalStateException("Chain ID already has different EL: " + chainId);
                }
                loadedEl.put(chainId, el);
            }
            return;
        }
        LiteFlowChainELBuilder.createChain().setChainName(chainId).setEL(el).build();
        loadedEl.put(chainId, el);
    }

    public boolean contains(String chainId) {
        return FlowBus.containChain(chainId);
    }
}
