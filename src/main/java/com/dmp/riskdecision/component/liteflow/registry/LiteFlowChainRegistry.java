package com.dmp.riskdecision.component.liteflow.registry;

import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.flow.FlowBus;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** LiteFlow 动态流程链的内部注册表。 */
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
