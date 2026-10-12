package com.dmp.riskdecision.component.liteflow.context;

import java.util.ArrayList;
import java.util.List;

/** 示例流程节点共享的执行标记。 */
public class FlowResult {

    private final List<String> markers = new ArrayList<>();

    public void addMarker(String marker) {
        markers.add(marker);
    }

    public List<String> getMarkers() {
        return List.copyOf(markers);
    }
}
