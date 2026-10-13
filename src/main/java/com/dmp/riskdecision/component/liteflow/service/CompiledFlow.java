package com.dmp.riskdecision.component.liteflow.service;

import java.util.Map;
import java.util.Objects;

/** 编译后的流程。 */
public class CompiledFlow {
    private final String chainId;
    private final String el;
    private final Map<String, String> nodeBindings;

    public CompiledFlow(String chainId, String el, Map<String, String> nodeBindings) {
        this.chainId = chainId;
        this.el = el;
        this.nodeBindings = nodeBindings;
    }

    public String getChainId() {
        return chainId;
    }

    public String getEl() {
        return el;
    }

    public Map<String, String> getNodeBindings() {
        return nodeBindings;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CompiledFlow)) {
            return false;
        }
        CompiledFlow that = (CompiledFlow) other;
        return Objects.equals(chainId, that.chainId)
                && Objects.equals(el, that.el)
                && Objects.equals(nodeBindings, that.nodeBindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chainId, el, nodeBindings);
    }
}
