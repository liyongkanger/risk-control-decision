package com.dmp.riskdecision.policy.vo.resp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 独立流程资产详情。 */
@Data
@AllArgsConstructor
public class FlowInfoResp {
    private long id;
    private String flowCode;
    private String flowName;
    private String flowType;
    private Long publishedVersionId;
    private Long publishedVersionNumber;
    private LocalDateTime updatetime;
}
