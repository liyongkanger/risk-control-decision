package com.dmp.riskdecision.policy.assembler;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowValidation;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.vo.req.FlowGraphReq;
import com.dmp.riskdecision.policy.vo.resp.FlowDraftResp;
import com.dmp.riskdecision.policy.vo.resp.FlowValidationResp;
import com.dmp.riskdecision.policy.vo.resp.PublishFlowResp;
import org.springframework.stereotype.Component;

/** 场景流程接口对象与内部对象的转换。 */
@Component
public class FlowAdminAssembler {
    public GraphDefinition toGraphDefinition(FlowGraphReq request) {
        return new GraphDefinition(request.getSchemaVersion(), request.getNodes(),
                request.getEdges(), request.getLayout());
    }

    public FlowDraftResp toDraftResp(FlowDraft draft) {
        FlowDraftResp response = new FlowDraftResp();
        response.setSceneCode(draft.getSceneCode());
        response.setGraph(draft.getGraph());
        response.setRevision(draft.getRevision());
        return response;
    }

    public FlowValidationResp toValidationResp(FlowValidation validation) {
        FlowValidationResp response = new FlowValidationResp();
        response.setValid(validation.isValid());
        response.setIssues(validation.getIssues());
        response.setCompiledEl(validation.getCompiledEl());
        return response;
    }

    public PublishFlowResp toPublishResp(FlowVersion version) {
        PublishFlowResp response = new PublishFlowResp();
        response.setId(version.getId());
        response.setSceneCode(version.getSceneCode());
        response.setVersionNumber(version.getVersionNumber());
        response.setChainId(version.getChainId());
        return response;
    }
}
