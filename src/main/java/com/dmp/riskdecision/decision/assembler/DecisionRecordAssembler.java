package com.dmp.riskdecision.decision.assembler;

import com.dmp.riskdecision.decision.model.DecisionRecord;
import com.dmp.riskdecision.decision.model.DecisionRecordPage;
import com.dmp.riskdecision.decision.vo.resp.DecisionRecordPageResp;
import com.dmp.riskdecision.decision.vo.resp.DecisionRecordResp;
import org.springframework.stereotype.Component;

/** 将决策执行记录转换为列表及详情响应。 */
@Component
public class DecisionRecordAssembler {
    public DecisionRecordResp toResp(DecisionRecord record) {
        DecisionRecordResp response = new DecisionRecordResp();
        response.setExecutionId(record.getExecutionId());
        response.setRequestId(record.getRequestId());
        response.setSceneCode(record.getSceneCode());
        response.setVersionId(record.getVersionId());
        response.setStatus(record.getStatus());
        response.setConclusion(record.getConclusion());
        response.setErrorCode(record.getErrorCode());
        response.setCreatedAt(record.getCreatedAt());
        response.setInput(record.getInput());
        response.setResult(record.getResult());
        response.setInputJson(record.getInputJson());
        response.setResultJson(record.getResultJson());
        return response;
    }

    public DecisionRecordPageResp toPageResp(DecisionRecordPage page) {
        DecisionRecordPageResp response = new DecisionRecordPageResp();
        response.setRecords(page.getRecords().stream().map(this::toResp).toList());
        response.setTotal(page.getTotal());
        response.setPage(page.getPage());
        response.setPageSize(page.getPageSize());
        return response;
    }
}
