package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;

public record FlowVersion(long id, String sceneCode, long versionNumber,
                          GraphDefinition graph, String chainId, String compiledEl) {}
