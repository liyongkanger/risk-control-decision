package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;

public record FlowDraft(String sceneCode, GraphDefinition graph, long revision) {}
