package com.dmp.riskdecision.orchestrator.graph;

import java.util.List;
import java.util.Map;

public record GraphDefinition(int schemaVersion, List<GraphNode> nodes,
                              List<GraphEdge> edges, Map<String, Point> layout) {}
