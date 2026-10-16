package com.dmp.riskdecision.decision.controller;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.decision.assembler.DecisionRecordAssembler;
import com.dmp.riskdecision.decision.service.DecisionRecordService;
import com.dmp.riskdecision.decision.vo.resp.DecisionRecordPageResp;
import com.dmp.riskdecision.decision.vo.resp.DecisionRecordResp;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 跨场景查询每次决策执行的固定快照。 */
@RestController
@RequestMapping("/api/decision-records")
public class DecisionRecordController {
    @Resource private DecisionRecordService records;
    @Resource private DecisionRecordAssembler assembler;

    @GetMapping
    public ApiResponse<DecisionRecordPageResp> search(
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(assembler.toPageResp(
                records.search(sceneCode, requestId, status, page, pageSize)));
    }

    @GetMapping("/{executionId}")
    public ApiResponse<DecisionRecordResp> get(@PathVariable String executionId) {
        return ApiResponse.success(assembler.toResp(records.get(executionId)));
    }
}
