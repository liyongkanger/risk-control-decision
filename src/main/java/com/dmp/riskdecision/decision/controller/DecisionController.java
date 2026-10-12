package com.dmp.riskdecision.decision.controller;

import com.dmp.riskdecision.decision.assembler.DecisionAssembler;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.decision.vo.req.EvaluateDecisionReq;
import com.dmp.riskdecision.decision.vo.resp.EvaluateDecisionResp;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 场景决策执行接口。 */
@RestController
@RequestMapping("/api/decisions")
public class DecisionController {
    private static final Logger LOG = LoggerFactory.getLogger(DecisionController.class);

    @Resource
    private DecisionExecutionService execution;

    @Resource
    private DecisionAssembler assembler;

    @PostMapping("/evaluate")
    public EvaluateDecisionResp evaluate(@RequestBody EvaluateDecisionReq request) {
        LOG.info("[DecisionController][evaluate] requestId:{} sceneCode:{}",
                request.getRequestId(), request.getSceneCode());
        return assembler.toEvaluateResp(execution.evaluate(request.getRequestId(),
                request.getSceneCode(), request.getInput()));
    }
}
