package com.dmp.riskdecision.component;

import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LiteFlowBaselineTest {

    @Autowired
    private FlowExecutor flowExecutor;

    @Test
    void executesTheBaselineChain() {
        FlowResult result = new FlowResult();

        LiteflowResponse response = flowExecutor.execute2Resp("baselineChain", null, result);

        assertThat(response.isSuccess()).isTrue();
        assertThat(new HashSet<>(result.getMarkers()))
                .containsExactlyInAnyOrder("prepare", "parallel-left", "parallel-right");
    }
}
