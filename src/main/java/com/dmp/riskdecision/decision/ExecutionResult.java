package com.dmp.riskdecision.decision;

import com.dmp.riskdecision.audit.NodeTrace;

import java.util.List;
import java.util.Map;

public record ExecutionResult(String requestId, String sceneCode, long versionId,
                              String status, Map<String, Object> nodeResults,
                              List<NodeTrace> trace, String errorCode) {}
