package com.dmp.riskdecision.policy.controller;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.common.response.ResultCode;
import com.dmp.riskdecision.policy.assembler.FlowAdminAssembler;
import com.dmp.riskdecision.policy.assembler.FlowAssetAssembler;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.vo.req.CreateFlowReq;
import com.dmp.riskdecision.policy.vo.req.FlowGraphReq;
import com.dmp.riskdecision.policy.vo.req.PublishFlowReq;
import com.dmp.riskdecision.policy.vo.resp.FlowAssetDraftResp;
import com.dmp.riskdecision.policy.vo.resp.FlowAssetVersionResp;
import com.dmp.riskdecision.policy.vo.resp.FlowInfoResp;
import com.dmp.riskdecision.policy.vo.resp.FlowValidationResp;
import com.dmp.riskdecision.policy.vo.resp.PageFlowResp;
import jakarta.annotation.Resource;
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

/** 脱离业务场景管理流程资产、草稿及发布版本。 */
@RestController
@RequestMapping("/api/flows")
public class FlowAssetController {
    @Resource
    private FlowAssetService flows;
    @Resource
    private FlowAssetAssembler assembler;
    @Resource
    private FlowAdminAssembler graphAssembler;

    @GetMapping
    public ApiResponse<PageFlowResp> search(@RequestParam(defaultValue = "") String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(assembler.toResp(flows.search(keyword, page, pageSize)));
    }

    @PostMapping
    public ApiResponse<FlowInfoResp> create(@RequestBody CreateFlowReq request) {
        return ApiResponse.success(assembler.toResp(flows.create(request.getFlowCode(), request.getFlowName())));
    }

    @GetMapping("/{flowCode}")
    public ApiResponse<FlowInfoResp> get(@PathVariable String flowCode) {
        return ApiResponse.success(assembler.toResp(flows.get(flowCode)));
    }

    @PutMapping("/{flowCode}/draft")
    public ApiResponse<FlowAssetDraftResp> saveDraft(@PathVariable String flowCode,
                                                      @RequestParam long expectedRevision,
                                                      @RequestBody FlowGraphReq request) {
        return ApiResponse.success(assembler.toResp(flows.saveDraft(flowCode,
                graphAssembler.toGraphDefinition(request), expectedRevision)));
    }

    @GetMapping("/{flowCode}/draft")
    public ApiResponse<FlowAssetDraftResp> getDraft(@PathVariable String flowCode) {
        return ApiResponse.success(assembler.toResp(flows.getDraft(flowCode)));
    }

    @PostMapping("/{flowCode}/validate")
    public ResponseEntity<ApiResponse<FlowValidationResp>> validate(@PathVariable String flowCode,
                                                                     @RequestBody FlowGraphReq request) {
        FlowValidationResp result = graphAssembler.toValidationResp(flows.validate(flowCode,
                graphAssembler.toGraphDefinition(request)));
        return result.isValid() ? ResponseEntity.ok(ApiResponse.success(result))
                : ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body(ApiResponse.failure(ResultCode.GRAPH_INVALID, ResultCode.GRAPH_INVALID.getMessage(), result));
    }

    @PostMapping("/{flowCode}/publish")
    public ApiResponse<FlowAssetVersionResp> publish(@PathVariable String flowCode,
                                                      @RequestBody PublishFlowReq request) {
        return ApiResponse.success(assembler.toResp(flows.publish(flowCode, request.getExpectedRevision())));
    }
}
