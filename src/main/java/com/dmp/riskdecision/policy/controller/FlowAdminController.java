package com.dmp.riskdecision.policy.controller;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.common.response.ResultCode;
import com.dmp.riskdecision.policy.assembler.FlowAdminAssembler;
import com.dmp.riskdecision.policy.service.FlowPublicationService;
import com.dmp.riskdecision.policy.vo.req.FlowGraphReq;
import com.dmp.riskdecision.policy.vo.req.PublishFlowReq;
import com.dmp.riskdecision.policy.vo.resp.FlowDraftResp;
import com.dmp.riskdecision.policy.vo.resp.FlowValidationResp;
import com.dmp.riskdecision.policy.vo.resp.PublishFlowResp;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 场景流程的草稿、校验和发布接口。 */
@RestController
@RequestMapping("/api/scenes/{sceneCode}")
public class FlowAdminController {
    private static final Logger LOG = LoggerFactory.getLogger(FlowAdminController.class);

    @Resource
    private FlowPublicationService publication;

    @Resource
    private FlowAdminAssembler assembler;

    @PutMapping("/draft")
    public ApiResponse<FlowDraftResp> saveDraft(@PathVariable String sceneCode, @RequestParam long expectedRevision,
                                   @RequestBody FlowGraphReq request) {
        LOG.info("[FlowAdminController][saveDraft] sceneCode:{} expectedRevision:{}", sceneCode, expectedRevision);
        return ApiResponse.success(assembler.toDraftResp(publication.saveDraft(sceneCode,
                assembler.toGraphDefinition(request), expectedRevision)));
    }

    @GetMapping("/draft")
    public ApiResponse<FlowDraftResp> getDraft(@PathVariable String sceneCode) {
        LOG.info("[FlowAdminController][getDraft] sceneCode:{}", sceneCode);
        return ApiResponse.success(assembler.toDraftResp(publication.getDraft(sceneCode)));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<FlowValidationResp>> validate(@PathVariable String sceneCode,
                                                       @RequestBody FlowGraphReq request) {
        LOG.info("[FlowAdminController][validate] sceneCode:{}", sceneCode);
        FlowValidationResp result = assembler.toValidationResp(publication.validate(sceneCode,
                assembler.toGraphDefinition(request)));
        return result.isValid() ? ResponseEntity.ok(ApiResponse.success(result))
                : ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body(ApiResponse.failure(ResultCode.GRAPH_INVALID, ResultCode.GRAPH_INVALID.getMessage(), result));
    }

    @PostMapping("/publish")
    public ApiResponse<PublishFlowResp> publish(@PathVariable String sceneCode, @RequestBody PublishFlowReq request) {
        LOG.info("[FlowAdminController][publish] sceneCode:{} expectedRevision:{}",
                sceneCode, request.getExpectedRevision());
        return ApiResponse.success(assembler.toPublishResp(publication.publish(sceneCode,
                request.getExpectedRevision())));
    }
}
