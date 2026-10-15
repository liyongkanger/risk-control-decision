package com.dmp.riskdecision.component.variable.controller;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.component.variable.vo.req.CreateVariableReq;
import com.dmp.riskdecision.component.variable.vo.req.TestVariableReq;
import com.dmp.riskdecision.component.variable.vo.req.UpdateVariableReq;
import com.dmp.riskdecision.component.variable.vo.resp.VariablePageResp;
import com.dmp.riskdecision.component.variable.vo.resp.VariableResp;
import com.dmp.riskdecision.component.variable.vo.resp.VariableTestResp;
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

/** 变量定义、版本及试取值的后台接口。 */
@RestController
@RequestMapping("/api/variables")
public class VariableController {
    @Resource private VariableService variables;

    @GetMapping
    public ApiResponse<VariablePageResp> search(@RequestParam(defaultValue = "") String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(new VariablePageResp(variables.search(keyword, page, pageSize)));
    }

    @GetMapping("/{code}")
    public ApiResponse<VariableResp> current(@PathVariable String code) {
        return ApiResponse.success(new VariableResp(variables.current(code)));
    }

    @GetMapping("/versions/{id}")
    public ApiResponse<VariableResp> version(@PathVariable long id) {
        return ApiResponse.success(new VariableResp(variables.requireVersion(id)));
    }

    @PostMapping
    public ApiResponse<VariableResp> create(@RequestBody CreateVariableReq request) {
        return ApiResponse.success(new VariableResp(variables.create(request.getVariableCode(),
                request.getVariableName(), request.getValueType(), request.getSourceType(), request.getUrl(),
                request.getTimeoutMs(), request.getParameterNames(), request.getResultPath())));
    }

    @PutMapping("/{code}")
    public ApiResponse<VariableResp> update(@PathVariable String code, @RequestBody UpdateVariableReq request) {
        return ApiResponse.success(new VariableResp(variables.update(code, request.getExpectedVersion(),
                request.getVariableName(), request.getValueType(), request.getSourceType(), request.getUrl(),
                request.getTimeoutMs(), request.getParameterNames(), request.getResultPath())));
    }

    @PostMapping("/{code}/test")
    public ApiResponse<VariableTestResp> test(@PathVariable String code, @RequestBody TestVariableReq request) {
        VariableVersion version = variables.current(code);
        Object value = variables.resolve(version.getId(), code,
                request.getInput() == null ? Map.of() : request.getInput());
        return ApiResponse.success(new VariableTestResp(code, version.getId(), version.getValueType(), value));
    }
}
