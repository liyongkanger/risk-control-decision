package com.dmp.riskdecision.component.variable.controller;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.model.VariableCategory;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.component.variable.vo.req.CreateVariableReq;
import com.dmp.riskdecision.component.variable.vo.req.TestVariableReq;
import com.dmp.riskdecision.component.variable.vo.req.UpdateVariableReq;
import com.dmp.riskdecision.component.variable.vo.req.UpdateVariableCategoryReq;
import com.dmp.riskdecision.component.variable.vo.resp.VariableCategoryResp;
import com.dmp.riskdecision.component.variable.vo.resp.VariablePageResp;
import com.dmp.riskdecision.component.variable.vo.resp.VariableResp;
import com.dmp.riskdecision.component.variable.vo.resp.VariableTestResp;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
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
                                                 @RequestParam(required = false) String categoryCode,
                                                 @RequestParam(required = false) String sourceType,
                                                 @RequestParam(required = false) String valueType,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(new VariablePageResp(variables.search(keyword, categoryCode,
                sourceType, valueType, page, pageSize)));
    }

    @GetMapping("/categories")
    public ApiResponse<List<VariableCategoryResp>> categories() {
        return ApiResponse.success(Arrays.stream(VariableCategory.values()).map(VariableCategoryResp::new).toList());
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
                request.getTimeoutMs(), request.getParameterNames(), request.getResultPath(),
                request.getDependencyVersionIds(), request.getCategoryCode())));
    }

    @PutMapping("/{code}/category")
    public ApiResponse<VariableResp> updateCategory(@PathVariable String code,
                                                     @RequestBody UpdateVariableCategoryReq request) {
        return ApiResponse.success(new VariableResp(variables.updateCategory(code, request.getCategoryCode())));
    }

    @PutMapping("/{code}")
    public ApiResponse<VariableResp> update(@PathVariable String code, @RequestBody UpdateVariableReq request) {
        return ApiResponse.success(new VariableResp(variables.update(code, request.getExpectedVersion(),
                request.getVariableName(), request.getValueType(), request.getSourceType(), request.getUrl(),
                request.getTimeoutMs(), request.getParameterNames(), request.getResultPath(),
                request.getDependencyVersionIds())));
    }

    @PostMapping("/{code}/test")
    public ApiResponse<VariableTestResp> test(@PathVariable String code, @RequestBody TestVariableReq request) {
        VariableVersion version = variables.current(code);
        Map<String, Object> input = request.getInput() == null ? Map.of() : request.getInput();
        Object value = variables.resolveForTest(version.getId(), code, input, input);
        return ApiResponse.success(new VariableTestResp(code, version.getId(), version.getValueType(), value));
    }
}
