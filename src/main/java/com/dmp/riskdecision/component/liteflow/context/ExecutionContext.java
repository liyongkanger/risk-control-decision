package com.dmp.riskdecision.component.liteflow.context;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/** 单次流程执行上下文。 */
public class ExecutionContext {
    private final String requestId;
    private final String sceneCode;
    private final long version;
    private final Map<String, Object> input;
    private final ConcurrentMap<String, Object> nodeResults;
    private final Queue<NodeTrace> traces;
    private final Map<String, Map<String, Object>> nodeConfigs;
    private final Map<String, FlowContextBinding> contextBindings;
    private final AtomicReference<Map<String, Object>> businessResultConfig = new AtomicReference<>();

    public ExecutionContext(String requestId, String sceneCode, long version, Map<String, Object> input, ConcurrentMap<String, Object> nodeResults, Queue<NodeTrace> traces, Map<String, Map<String, Object>> nodeConfigs) {
        this(requestId, sceneCode, version, input, nodeResults, traces, nodeConfigs, Map.of());
    }

    public ExecutionContext(String requestId, String sceneCode, long version, Map<String, Object> input,
                            ConcurrentMap<String, Object> nodeResults, Queue<NodeTrace> traces,
                            Map<String, Map<String, Object>> nodeConfigs,
                            Map<String, FlowContextBinding> contextBindings) {
        this.requestId = requestId;
        this.sceneCode = sceneCode;
        this.version = version;
        this.input = input;
        this.nodeResults = nodeResults;
        this.traces = traces;
        this.nodeConfigs = nodeConfigs;
        this.contextBindings = contextBindings;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public long getVersion() {
        return version;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public ConcurrentMap<String, Object> getNodeResults() {
        return nodeResults;
    }

    public Queue<NodeTrace> getTraces() {
        return traces;
    }

    public Map<String, Map<String, Object>> getNodeConfigs() {
        return nodeConfigs;
    }

    public Map<String, FlowContextBinding> getContextBindings() {
        return contextBindings;
    }

    /** 本次运行只能写入一次最终业务结果配置。 */
    public void setBusinessResultConfig(Map<String, Object> config) {
        if (config == null || !businessResultConfig.compareAndSet(null,
                Collections.unmodifiableMap(new LinkedHashMap<>(config)))) {
            throw new IllegalStateException("Business result was already set or is missing");
        }
    }

    public Map<String, Object> getBusinessResultConfig() {
        return businessResultConfig.get();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExecutionContext)) {
            return false;
        }
        ExecutionContext that = (ExecutionContext) other;
        return Objects.equals(requestId, that.requestId)
                && Objects.equals(sceneCode, that.sceneCode)
                && version == that.version
                && Objects.equals(input, that.input)
                && Objects.equals(nodeResults, that.nodeResults)
                && Objects.equals(traces, that.traces)
                && Objects.equals(nodeConfigs, that.nodeConfigs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, sceneCode, version, input, nodeResults, traces, nodeConfigs);
    }
}
