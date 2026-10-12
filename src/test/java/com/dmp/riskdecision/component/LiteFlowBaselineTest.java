package com.dmp.riskdecision.component;

import com.dmp.riskdecision.component.liteflow.context.FlowResult;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import jakarta.annotation.Resource;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** LiteFlow 基础节点链路验证。 */
@SpringBootTest
class LiteFlowBaselineTest {

    @Resource
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
