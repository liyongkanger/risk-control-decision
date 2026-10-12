package com.dmp.riskdecision.component.liteflow.node.demo;

import com.dmp.riskdecision.component.liteflow.context.FlowResult;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

/** 示例流程的准备节点。 */
@LiteflowComponent("prepare")
public class PrepareComponent extends NodeComponent {

    @Override
    public void process() {
        getContextBean(FlowResult.class).addMarker("prepare");
    }
}
