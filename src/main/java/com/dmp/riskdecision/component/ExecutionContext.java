package com.dmp.riskdecision.component;

import com.dmp.riskdecision.audit.NodeTrace;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;

public record ExecutionContext(String requestId, String sceneCode, long version,
                               Map<String, Object> input, ConcurrentMap<String, Object> nodeResults,
                               Queue<NodeTrace> traces, Map<String, Map<String, Object>> nodeConfigs) {}
