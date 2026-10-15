package com.dmp.riskdecision.component.liteflow.controller;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogPage;
import com.dmp.riskdecision.component.liteflow.vo.resp.ComponentDefinitionResp;
import com.dmp.riskdecision.component.liteflow.vo.resp.ComponentPageResp;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 向流程编辑器提供平台已登记组件及配置字段。 */
@RestController
@RequestMapping("/api/components")
public class ComponentCatalogController {
    @Resource private ComponentCatalogService catalog;

    @GetMapping
    public ApiResponse<List<ComponentDefinitionResp>> list() {
        return ApiResponse.success(catalog.list().stream().map(ComponentDefinitionResp::new).toList());
    }

    @GetMapping("/page")
    public ApiResponse<ComponentPageResp> search(@RequestParam(defaultValue = "") String keyword,
                                                 @RequestParam(defaultValue = "") String nodeKind,
                                                 @RequestParam(defaultValue = "") String category,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        ComponentCatalogPage result = catalog.search(keyword, nodeKind, category, page, pageSize);
        return ApiResponse.success(new ComponentPageResp(result.getRecords().stream()
                .map(ComponentDefinitionResp::new).toList(), result.getTotal(),
                result.getPage(), result.getPageSize()));
    }
}
