package com.dmp.riskdecision.decision.service.impl;

import com.dmp.riskdecision.component.liteflow.service.SubflowInvoker;
import com.dmp.riskdecision.component.liteflow.service.SubflowInvocationException;
import com.dmp.riskdecision.decision.model.BusinessResult;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.model.SuggestedAction;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.FlowAssetRepository;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** 通过固定的不可变流程版本执行业务子流程。 */
@Service
public class SubflowInvokerImpl implements SubflowInvoker {
    @Resource
    private FlowAssetRepository repository;
    @Resource
    private DecisionExecutionServiceImpl decisions;

    @Override
    public Map<String, Object> invoke(String requestId, String flowCode, long versionId, Map<String, Object> input) {
        FlowVersion version = repository.findVersion(versionId)
                .orElseThrow(() -> new NoSuchElementException("Subflow version not found: " + versionId));
        if (!flowCode.equals(version.getFlowCode()) || version.getGraph().getNodes().stream()
                .anyMatch(node -> node.getKind() == NodeKind.SUBFLOW)) {
            throw new IllegalStateException("Subflow version is invalid for this call");
        }
        ExecutionResult result = decisions.evaluateVersion(requestId, flowCode, version, input);
        if (!"SUCCEEDED".equals(result.getStatus()) || result.getBusinessResult() == null) {
            throw new SubflowInvocationException(flowCode, versionId,
                    result.getErrorCode() == null ? "BUSINESS_RESULT_MISSING" : result.getErrorCode(),
                    result.getTrace());
        }
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("flowCode", flowCode);
        call.put("versionId", versionId);
        call.put("status", result.getStatus());
        call.put("businessResult", toMap(result.getBusinessResult()));
        call.put("trace", result.getTrace());
        return Map.copyOf(call);
    }

    private static Map<String, Object> toMap(BusinessResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("conclusion", result.getConclusion());
        value.put("reasonCode", result.getReasonCode());
        value.put("reason", result.getReason());
        value.put("details", result.getDetails());
        SuggestedAction action = result.getSuggestedAction();
        if (action != null) {
            value.put("suggestedAction", Map.of("code", action.getCode(), "parameters", action.getParameters()));
        }
        return Map.copyOf(value);
    }
}
