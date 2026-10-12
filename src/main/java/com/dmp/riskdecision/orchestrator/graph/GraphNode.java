package com.dmp.riskdecision.orchestrator.graph;

import java.util.Map;

public record GraphNode(String id, NodeKind kind, String componentKey,
                        FailurePolicy failurePolicy, Map<String, Object> config) {}
