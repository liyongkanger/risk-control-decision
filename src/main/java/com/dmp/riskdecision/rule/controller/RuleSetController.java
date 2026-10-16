package com.dmp.riskdecision.rule.controller;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.rule.service.RuleSetService;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.vo.req.CreateRuleSetReq;
import com.dmp.riskdecision.rule.vo.req.PublishRuleSetReq;
import com.dmp.riskdecision.rule.vo.req.TestRuleSetReq;
import com.dmp.riskdecision.rule.vo.req.UpdateRuleDraftReq;
import com.dmp.riskdecision.rule.vo.resp.RuleEvaluationResp;
import com.dmp.riskdecision.rule.vo.resp.RuleSetPageResp;
import com.dmp.riskdecision.rule.vo.resp.RuleSetResp;
import com.dmp.riskdecision.rule.vo.resp.RuleVersionResp;
import jakarta.annotation.Resource;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 规则集草稿、试算和版本发布的后台接口。 */
@RestController
@RequestMapping("/api/rule-sets")
public class RuleSetController {
    @Resource private RuleSetService rules;

    @GetMapping
    public ApiResponse<RuleSetPageResp> search(@RequestParam(defaultValue = "") String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(new RuleSetPageResp(rules.search(keyword, page, pageSize)));
    }

    @PostMapping
    public ApiResponse<RuleSetResp> create(@RequestBody CreateRuleSetReq request) {
        return ApiResponse.success(new RuleSetResp(rules.create(request.getCode(), request.getName(), request.getDefinition())));
    }

    @GetMapping("/{code}")
    public ApiResponse<RuleSetResp> get(@PathVariable String code) {
        return ApiResponse.success(new RuleSetResp(rules.get(code)));
    }

    @PutMapping("/{code}/draft")
    public ApiResponse<RuleSetResp> update(@PathVariable String code, @RequestBody UpdateRuleDraftReq request) {
        return ApiResponse.success(new RuleSetResp(rules.updateDraft(code, request.getExpectedRevision(), request.getDefinition())));
    }

    @PostMapping("/{code}/test")
    public ApiResponse<RuleEvaluationResp> test(@PathVariable String code, @RequestBody TestRuleSetReq request) {
        return ApiResponse.success(new RuleEvaluationResp(rules.testDraft(code,
                request.getInput() == null ? Map.of() : request.getInput(),
                request.getContextValues() == null ? Map.of() : request.getContextValues())));
    }

    @PostMapping("/{code}/versions/{id}/test")
    public ApiResponse<RuleEvaluationResp> testVersion(@PathVariable String code, @PathVariable long id,
                                                       @RequestBody TestRuleSetReq request) {
        RuleSetVersion version = rules.requireVersion(id);
        if (!code.equals(version.getCode())) throw new IllegalArgumentException("Rule version does not belong to rule set");
        return ApiResponse.success(new RuleEvaluationResp(rules.testVersion(id,
                request.getInput() == null ? Map.of() : request.getInput(),
                request.getContextValues() == null ? Map.of() : request.getContextValues())));
    }

    @PostMapping("/{code}/publish")
    public ApiResponse<RuleVersionResp> publish(@PathVariable String code, @RequestBody PublishRuleSetReq request) {
        return ApiResponse.success(new RuleVersionResp(rules.publish(code, request.getExpectedRevision())));
    }

    @GetMapping("/versions/{id}")
    public ApiResponse<RuleVersionResp> version(@PathVariable long id) {
        return ApiResponse.success(new RuleVersionResp(rules.requireVersion(id)));
    }
}
