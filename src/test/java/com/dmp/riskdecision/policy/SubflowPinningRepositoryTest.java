package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.FlowAssetRepository;
import jakarta.annotation.Resource;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 外层发布指针切换时再次核对所选子流程版本。 */
@SpringBootTest
class SubflowPinningRepositoryTest {
    @Resource
    private FlowAssetRepository flows;

    @Test
    void staleChildVersionCannotBeCommittedAsANewOuterPublication() {
        String suffix = Long.toString(System.nanoTime());
        String child = "pinChild" + suffix;
        String outer = "pinOuter" + suffix;
        flows.create(child, "子流程");
        flows.saveDraft(child, FlowPublicationServiceTest.simpleGraph(1), 0);
        FlowVersion childV1 = flows.createVersion(child, 1,
                FlowPublicationServiceTest.simpleGraph(1), "flow_" + child + "_v1", "EL");
        flows.switchPublished(child, null, childV1.getId(), 1);
        flows.saveDraft(child, FlowPublicationServiceTest.simpleGraph(2), 1);
        FlowVersion childV2 = flows.createVersion(child, 2,
                FlowPublicationServiceTest.simpleGraph(2), "flow_" + child + "_v2", "EL");
        flows.switchPublished(child, childV1.getId(), childV2.getId(), 2);

        flows.create(outer, "外层流程");
        flows.saveDraft(outer, FlowPublicationServiceTest.simpleGraph(3), 0);
        FlowVersion candidate = flows.createVersion(outer, 1,
                FlowPublicationServiceTest.simpleGraph(3), "flow_" + outer + "_v1", "EL");

        assertThatThrownBy(() -> flows.switchPublished(outer, null, candidate.getId(), 1,
                Map.of(child, childV1.getId()))).isInstanceOf(IllegalStateException.class);
        assertThat(flows.require(outer).getPublishedVersionId()).isNull();
    }
}
