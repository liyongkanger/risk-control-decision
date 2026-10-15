package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.component.liteflow.compile.FlowCompiler;
import com.dmp.riskdecision.component.liteflow.registry.ComponentRegistry;
import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.ResultPathValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 营销样板三条流程必须沿用真实图校验与 EL 编译能力。 */
class MarketingDemoFlowsTest {
    @Test
    void compilesEligibilitySelectionAndPinnedOuterResults() {
        MarketingDemoFlows definition = new MarketingDemoFlows();
        GraphDefinition eligibility = definition.eligibilityGraph(33);
        GraphDefinition selection = definition.selectionGraph();
        GraphDefinition outer = definition.outerGraph(11, 22);
        ResultPathValidator results = new ResultPathValidator();
        ComponentCatalogService catalog = new ComponentCatalogService(new ComponentRegistry());
        FlowCompiler compiler = new FlowCompiler(catalog);

        for (GraphDefinition graph : new GraphDefinition[]{eligibility, selection, outer}) {
            assertThat(results.validate(graph).isValid()).isTrue();
        }
        assertThat(compiler.compile("eligibility", 1, eligibility).getEl()).contains("IF(");
        assertThat(compiler.compile("selection", 1, selection).getEl()).contains("IF(");
        assertThat(compiler.compile("outer", 1, outer).getEl())
                .contains("decisionSubflow.tag(\"eligibilityCall\")")
                .contains("decisionSubflow.tag(\"selectionCall\")");
        assertThat(outer.getRequiredInputs()).containsExactly("orderAmount", "marginAfterMaxOffer",
                "remainingBudget", "inExperiment", "highGroup");
    }
}
