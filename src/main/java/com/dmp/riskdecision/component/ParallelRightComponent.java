package com.dmp.riskdecision.component;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

@LiteflowComponent("parallelRight")
public class ParallelRightComponent extends NodeComponent {

    @Override
    public void process() {
        getContextBean(FlowResult.class).addMarker("parallel-right");
    }
}
