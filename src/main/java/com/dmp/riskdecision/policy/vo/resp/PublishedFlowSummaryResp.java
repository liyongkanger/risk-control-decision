package com.dmp.riskdecision.policy.vo.resp;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 当前发布版本及其必需输入字段摘要，供画布选择子流程。 */
@Data
@AllArgsConstructor
public class PublishedFlowSummaryResp {
    private long id;
    private String flowCode;
    private long versionNumber;
    private List<String> requiredInputs;
}
