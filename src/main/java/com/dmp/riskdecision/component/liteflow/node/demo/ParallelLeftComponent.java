package com.dmp.riskdecision.component.liteflow.node.demo;

import com.dmp.riskdecision.component.liteflow.context.FlowResult;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

/** 并行分支左侧的示例节点。 */
@LiteflowComponent("parallelLeft")
public class ParallelLeftComponent extends NodeComponent {

    @Override
    public void process() {
        getContextBean(FlowResult.class).addMarker("parallel-left");
    }
}
