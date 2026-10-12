package com.dmp.riskdecision.component.liteflow.node.demo;

import com.dmp.riskdecision.component.liteflow.context.FlowResult;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

/** 并行分支右侧的示例节点。 */
@LiteflowComponent("parallelRight")
public class ParallelRightComponent extends NodeComponent {

    @Override
    public void process() {
        getContextBean(FlowResult.class).addMarker("parallel-right");
    }
}
