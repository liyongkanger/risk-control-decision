package com.dmp.riskdecision.policy.controller;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.policy.assembler.SceneAssembler;
import com.dmp.riskdecision.policy.service.SceneService;
import com.dmp.riskdecision.policy.vo.req.CreateSceneReq;
import com.dmp.riskdecision.policy.vo.req.BindSceneFlowReq;
import com.dmp.riskdecision.policy.vo.req.UpdateSceneReq;
import com.dmp.riskdecision.policy.vo.resp.PageSceneResp;
import com.dmp.riskdecision.policy.vo.resp.SceneResp;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 场景基本信息的管理接口。 */
@RestController
@RequestMapping("/api/scenes")
public class SceneController {
    @Resource
    private SceneService scenes;

    @Resource
    private SceneAssembler assembler;

    @GetMapping
    public ApiResponse<PageSceneResp> search(@RequestParam(defaultValue = "") String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(assembler.toPageResp(scenes.search(keyword, page, pageSize)));
    }

    @PostMapping
    public ApiResponse<SceneResp> create(@RequestBody CreateSceneReq request) {
        return ApiResponse.success(assembler.toResp(scenes.create(request.getSceneCode(),
                request.getSceneName(), request.getDescription())));
    }

    @GetMapping("/{sceneCode}")
    public ApiResponse<SceneResp> get(@PathVariable String sceneCode) {
        return ApiResponse.success(assembler.toResp(scenes.get(sceneCode)));
    }

    @PutMapping("/{sceneCode}")
    public ApiResponse<SceneResp> update(@PathVariable String sceneCode,
                                         @RequestBody UpdateSceneReq request) {
        return ApiResponse.success(assembler.toResp(scenes.update(sceneCode,
                request.getSceneName(), request.getDescription())));
    }

    @PutMapping("/{sceneCode}/entry-flow")
    public ApiResponse<SceneResp> bindFlow(@PathVariable String sceneCode,
                                           @RequestBody BindSceneFlowReq request) {
        return ApiResponse.success(assembler.toResp(scenes.bindFlow(sceneCode, request.getFlowCode())));
    }
}
