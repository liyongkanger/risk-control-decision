package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.orchestrator.graph.GraphIssue;

import java.util.List;

public record FlowValidation(boolean valid, List<GraphIssue> issues, String compiledEl) {}
