package com.dmp.riskdecision.policy.assembler;

import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowInfo;
import com.dmp.riskdecision.policy.model.FlowPage;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.vo.resp.FlowAssetDraftResp;
import com.dmp.riskdecision.policy.vo.resp.FlowAssetVersionResp;
import com.dmp.riskdecision.policy.vo.resp.FlowInfoResp;
import com.dmp.riskdecision.policy.vo.resp.PageFlowResp;
import org.springframework.stereotype.Component;

/** 独立流程资产及版本的响应转换。 */
@Component
public class FlowAssetAssembler {
    public FlowInfoResp toResp(FlowInfo flow) {
        return new FlowInfoResp(flow.getId(), flow.getFlowCode(), flow.getFlowName(),
                flow.getFlowType(), flow.getPublishedVersionId(),
                flow.getPublishedVersionNumber(), flow.getUpdatetime());
    }

    public PageFlowResp toResp(FlowPage page) {
        return new PageFlowResp(page.getRecords().stream().map(this::toResp).toList(),
                page.getTotal(), page.getPage(), page.getPageSize());
    }

    public FlowAssetDraftResp toResp(FlowDraft draft) {
        return new FlowAssetDraftResp(draft.getFlowCode(), draft.getGraph(), draft.getRevision());
    }

    public FlowAssetVersionResp toResp(FlowVersion version) {
        return new FlowAssetVersionResp(version.getId(), version.getFlowCode(),
                version.getVersionNumber(), version.getChainId());
    }
}
