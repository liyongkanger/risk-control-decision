package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.model.SceneInfo;
import com.dmp.riskdecision.policy.service.SceneService;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 旧场景已选中的金额直传流程在首次新版本结算前自动迁移。 */
class MarketingDemoLegacyActivationTest {
    @Test
    void initializedLegacySceneIsMigratedBeforeCheckout() {
        SceneService scenes = mock(SceneService.class);
        MarketingDemoFlowService service = spy(new MarketingDemoFlowService());
        ReflectionTestUtils.setField(service, "scenes", scenes);
        when(scenes.get(MarketingDemoFlows.SCENE_CODE)).thenReturn(new SceneInfo(
                MarketingDemoFlows.SCENE_CODE, "演示场景", MarketingDemoFlows.OUTER_CODE,
                "", 1L, null));
        GraphDefinition legacy = new GraphDefinition(1, List.of(), List.of(), Map.of());
        legacy.setRequiredInputs(List.of("orderAmount", "marginAfterMaxOffer", "remainingBudget"));
        when(scenes.getSelectedFlowVersion(MarketingDemoFlows.SCENE_CODE))
                .thenReturn(new FlowVersion(1, MarketingDemoFlows.OUTER_CODE, 1, legacy, "old", ""));
        doReturn(new MarketingDemoSetup(MarketingDemoFlows.SCENE_CODE,
                MarketingDemoFlows.ELIGIBILITY_CODE, 2, MarketingDemoFlows.SELECTION_CODE, 3,
                MarketingDemoFlows.OUTER_CODE, 4)).when(service).setup();

        service.ensureInitialized();
        verify(service).setup();
    }

    @Test
    void outerFlowRepointsWhenPinnedEligibilityUsesLegacyBudgetContract() {
        MarketingDemoFlowService service = new MarketingDemoFlowService();
        FlowAssetService flows = mock(FlowAssetService.class);
        VariableService variables = mock(VariableService.class);
        ReflectionTestUtils.setField(service, "flows", flows);
        ReflectionTestUtils.setField(service, "variables", variables);
        String code = MarketingDemoFlows.OUTER_CODE;
        GraphDefinition oldOuter = outer(1);
        GraphDefinition updatedOuter = outer(2);
        GraphDefinition oldEligibility = new GraphDefinition(1, List.of(new GraphNode(
                "remainingBudgetValue", NodeKind.COMPONENT, "get-variable", null,
                Map.of("variableCode", MarketingDemoFlows.BUDGET_VARIABLE, "versionId", 99))),
                List.of(), Map.of());
        oldEligibility.setRequiredInputs(List.of("uid", "checkoutId", "experimentId"));
        when(flows.getPublished(code)).thenReturn(new FlowVersion(3, code, 1, oldOuter, "old", ""));
        when(flows.getVersion(1)).thenReturn(new FlowVersion(1, MarketingDemoFlows.ELIGIBILITY_CODE,
                1, oldEligibility, "eligibility", ""));
        when(variables.requireVersion(99)).thenReturn(new VariableVersion(99,
                MarketingDemoFlows.BUDGET_VARIABLE, "旧预算", 1, VariableValueType.DECIMAL,
                "DEMO_MARKETING", "marketing://budget", 1000, List.of("experimentId"),
                "remainingBudget"));
        when(flows.getDraft(code)).thenReturn(new FlowDraft(code, oldOuter, 1));
        when(flows.publish(code, 2)).thenReturn(new FlowVersion(4, code, 2, updatedOuter, "new", ""));

        FlowVersion published = ReflectionTestUtils.invokeMethod(service, "ensurePublished",
                code, "外层", updatedOuter);
        org.assertj.core.api.Assertions.assertThat(published.getId()).isEqualTo(4);
        verify(flows).saveDraft(code, updatedOuter, 1);
    }

    private GraphDefinition outer(long eligibilityVersionId) {
        GraphDefinition graph = new GraphDefinition(1, List.of(new GraphNode(
                "eligibilityCall", NodeKind.SUBFLOW, null, null,
                Map.of("flowCode", MarketingDemoFlows.ELIGIBILITY_CODE,
                        "versionId", eligibilityVersionId, "inputMappings", Map.of()))),
                List.of(), Map.of());
        graph.setRequiredInputs(List.of("uid", "checkoutId", "experimentId"));
        return graph;
    }
}
