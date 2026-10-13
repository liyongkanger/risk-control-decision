package com.dmp.riskdecision.policy.model;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import java.util.Objects;

/** 已保存的流程版本。 */
public class FlowVersion {
    private final long id;
    private final String flowCode;
    private final long versionNumber;
    private final GraphDefinition graph;
    private final String chainId;
    private final String compiledEl;

    public FlowVersion(long id, String flowCode, long versionNumber, GraphDefinition graph, String chainId, String compiledEl) {
        this.id = id;
        this.flowCode = flowCode;
        this.versionNumber = versionNumber;
        this.graph = graph;
        this.chainId = chainId;
        this.compiledEl = compiledEl;
    }

    public long getId() {
        return id;
    }

    public String getSceneCode() {
        return flowCode;
    }

    public String getFlowCode() {
        return flowCode;
    }

    public long getVersionNumber() {
        return versionNumber;
    }

    public GraphDefinition getGraph() {
        return graph;
    }

    public String getChainId() {
        return chainId;
    }

    public String getCompiledEl() {
        return compiledEl;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FlowVersion)) {
            return false;
        }
        FlowVersion that = (FlowVersion) other;
        return id == that.id
                && Objects.equals(flowCode, that.flowCode)
                && versionNumber == that.versionNumber
                && Objects.equals(graph, that.graph)
                && Objects.equals(chainId, that.chainId)
                && Objects.equals(compiledEl, that.compiledEl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, flowCode, versionNumber, graph, chainId, compiledEl);
    }
}
