package com.dmp.riskdecision.decision;

import com.dmp.riskdecision.decision.assembler.DecisionAssembler;
import com.dmp.riskdecision.decision.model.BusinessResult;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.model.SuggestedAction;
import com.dmp.riskdecision.decision.vo.resp.EvaluateDecisionResp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 业务结果契约与旧执行结果兼容性验证。 */
class DecisionResultContractTest {

    @Test
    void carriesARecommendedOfferWithoutMarkingItAsExecuted() {
        BusinessResult offer = new BusinessResult("LOW_VALUE_OFFER", "ELIGIBLE_LOW_GROUP",
                "符合活动条件", Map.of("offerCode", "LOW_5_OFF_100"),
                new SuggestedAction("APPLY_OFFER", Map.of("offerCode", "LOW_5_OFF_100")));

        assertThat(offer.getConclusion()).isEqualTo("LOW_VALUE_OFFER");
        assertThat(offer.getDetails()).containsEntry("offerCode", "LOW_5_OFF_100");
        assertThat(offer.getSuggestedAction().getCode()).isEqualTo("APPLY_OFFER");
        assertThat(offer.getSuggestedAction().getParameters()).containsEntry("offerCode", "LOW_5_OFF_100");
    }

    @Test
    void normalNoOfferHasNoSuggestedAction() {
        BusinessResult noOffer = new BusinessResult("NO_NEW_OFFER", "NOT_ELIGIBLE",
                "未满足活动条件", null, null);

        assertThat(noOffer.getConclusion()).isEqualTo("NO_NEW_OFFER");
        assertThat(noOffer.getDetails()).isEmpty();
        assertThat(noOffer.getSuggestedAction()).isNull();
    }

    @Test
    void rejectsBlankBusinessCodesAndReason() {
        assertThatThrownBy(() -> new BusinessResult(" ", "REASON", "说明", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessResult("NO_NEW_OFFER", " ", "说明", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessResult("NO_NEW_OFFER", "REASON", " ", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SuggestedAction(" ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oldExecutionResultHasNoBusinessResult() {
        ExecutionResult legacy = new ExecutionResult("request-1", "scene", 1, "SUCCEEDED",
                Map.of(), List.of(), null);

        assertThat(legacy.getBusinessResult()).isNull();
    }

    @Test
    void failedExecutionCannotCarryANormalBusinessResult() {
        BusinessResult noOffer = new BusinessResult("NO_NEW_OFFER", "NOT_ELIGIBLE",
                "未满足活动条件", Map.of(), null);

        assertThatThrownBy(() -> new ExecutionResult("request-1", "scene", 1, "FAILED",
                Map.of(), List.of(), "FLOW_EXECUTION_FAILED", noOffer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesBusinessResultThroughTheResponseContract() {
        BusinessResult offer = new BusinessResult("LOW_VALUE_OFFER", "ELIGIBLE_LOW_GROUP",
                "符合活动条件", Map.of("offerCode", "LOW_5_OFF_100"),
                new SuggestedAction("APPLY_OFFER", Map.of("offerCode", "LOW_5_OFF_100")));
        ExecutionResult execution = new ExecutionResult("checkout-1", "checkoutOffer", 42, "SUCCEEDED",
                Map.of("eligibility", true), List.of(), null, offer);

        EvaluateDecisionResp response = new DecisionAssembler().toEvaluateResp(execution);

        assertThat(response.getBusinessResult().getConclusion()).isEqualTo("LOW_VALUE_OFFER");
        assertThat(response.getBusinessResult().getReasonCode()).isEqualTo("ELIGIBLE_LOW_GROUP");
        assertThat(response.getBusinessResult().getReason()).isEqualTo("符合活动条件");
        assertThat(response.getBusinessResult().getDetails()).containsEntry("offerCode", "LOW_5_OFF_100");
        assertThat(response.getBusinessResult().getSuggestedAction().getCode()).isEqualTo("APPLY_OFFER");
        assertThat(response.getBusinessResult().getSuggestedAction().getParameters())
                .containsEntry("offerCode", "LOW_5_OFF_100");
        assertThat(response.getNodeResults()).containsEntry("eligibility", true);
    }

    @Test
    void legacyExecutionResponseKeepsNullBusinessResult() {
        ExecutionResult legacy = new ExecutionResult("request-1", "scene", 1, "SUCCEEDED",
                Map.of("emit", 1), List.of(), null);

        EvaluateDecisionResp response = new DecisionAssembler().toEvaluateResp(legacy);

        assertThat(response.getBusinessResult()).isNull();
        assertThat(response.getNodeResults()).containsEntry("emit", 1);
    }
}
