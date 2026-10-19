package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.service.RuleSetService;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 初始化或迁移固定样板时，不得覆盖已有的用户流程草稿。 */
class MarketingDemoDraftProtectionTest {
    @Test
    void legacyUpgradeRejectsEditedDraftBeforeSavingTemplate() {
        FlowAssetService flows = mock(FlowAssetService.class);
        MarketingDemoFlowService demo = service(flows);
        String code = MarketingDemoFlows.ELIGIBILITY_CODE;
        GraphDefinition published = graph("qualified");
        GraphDefinition edited = graph("customReview");
        when(flows.getPublished(code)).thenReturn(new FlowVersion(1, code, 1, published, "legacy", ""));
        when(flows.getDraft(code)).thenReturn(new FlowDraft(code, edited, 3));

        assertThatThrownBy(demo::setup).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft");
        verify(flows, never()).saveDraft(eq(code), any(), anyLong());
    }

    @Test
    void unpublishedAssetKeepsExistingDraftForManualReview() {
        FlowAssetService flows = mock(FlowAssetService.class);
        MarketingDemoFlowService demo = service(flows);
        String code = MarketingDemoFlows.ELIGIBILITY_CODE;
        when(flows.getPublished(code)).thenThrow(new NoSuchElementException("not published"));
        when(flows.getDraft(code)).thenReturn(new FlowDraft(code, graph("customReview"), 2));

        assertThatThrownBy(demo::setup).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft");
        verify(flows, never()).saveDraft(eq(code), any(), anyLong());
    }

    private MarketingDemoFlowService service(FlowAssetService flows) {
        MarketingDemoFlowService demo = new MarketingDemoFlowService();
        RuleSetService rules = mock(RuleSetService.class);
        when(rules.currentVersion(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE))
                .thenReturn(new RuleSetVersion(1, MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE, 1, null));
        ReflectionTestUtils.setField(demo, "rules", rules);
        VariableService variables = mock(VariableService.class);
        when(variables.current(any())).thenAnswer(call -> {
            String code = call.getArgument(0);
            if (List.of("uid", "checkoutId", "experimentId").contains(code)) {
                return new VariableVersion(1, code, "定位变量", 1, VariableValueType.STRING,
                        "REQUEST_INPUT", null, 0, List.of(), null);
            }
            return new VariableVersion(1, code, "样板变量", 1, VariableValueType.DECIMAL,
                    "DEMO_MARKETING", "marketing://checkout", 1000,
                    List.of("uid", "checkoutId", "experimentId"), "orderAmount",
                    Map.of("uid", 1L, "checkoutId", 1L, "experimentId", 1L));
        });
        ReflectionTestUtils.setField(demo, "variables", variables);
        ReflectionTestUtils.setField(demo, "flows", flows);
        ReflectionTestUtils.setField(demo, "scenes", mock(SceneService.class));
        return demo;
    }

    private GraphDefinition graph(String id) {
        GraphDefinition graph = new GraphDefinition(1,
                List.of(new GraphNode(id, NodeKind.CONDITION, "is-positive", null, Map.of("inputKey", "eligible"))),
                List.of(), Map.of());
        graph.setRequiredInputs(List.of("eligible"));
        return graph;
    }
}
