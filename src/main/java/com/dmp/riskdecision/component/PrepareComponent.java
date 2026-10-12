package com.dmp.riskdecision.component;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

@LiteflowComponent("prepare")
public class PrepareComponent extends NodeComponent {

    @Override
    public void process() {
        getContextBean(FlowResult.class).addMarker("prepare");
    }
}
