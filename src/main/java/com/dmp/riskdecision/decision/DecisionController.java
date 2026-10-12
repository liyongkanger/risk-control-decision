package com.dmp.riskdecision.decision;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/decisions")
public class DecisionController {
    private final DecisionExecutionService execution;

    public DecisionController(DecisionExecutionService execution) {
        this.execution = execution;
    }

    @PostMapping("/evaluate")
    public ExecutionResult evaluate(@RequestBody EvaluateRequest request) {
        return execution.evaluate(request.requestId(), request.sceneCode(), request.input());
    }

    public record EvaluateRequest(String requestId, String sceneCode, Map<String, Object> input) {}
}
