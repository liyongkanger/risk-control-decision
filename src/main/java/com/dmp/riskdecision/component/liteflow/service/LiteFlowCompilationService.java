package com.dmp.riskdecision.component.liteflow.service;

import com.dmp.riskdecision.component.liteflow.compile.FlowCompiler;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** 向业务模块提供流程图到 LiteFlow 执行链的编译能力。 */
@Service
public class LiteFlowCompilationService {
    @Resource
    private FlowCompiler compiler;

    public LiteFlowCompilationService() {
    }

    public LiteFlowCompilationService(FlowCompiler compiler) {
        this.compiler = compiler;
    }

    /**
     * 将通过结构校验的流程图转换为 LiteFlow EL。
     *
     * @see FlowCompiler#compile(String, long, GraphDefinition)
     */
    public CompiledFlow compile(String flowCode, long version, GraphDefinition graph) {
        return compiler.compile(flowCode, version, graph);
    }
}
