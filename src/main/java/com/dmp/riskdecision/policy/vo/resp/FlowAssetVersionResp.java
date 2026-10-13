package com.dmp.riskdecision.policy.vo.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 独立流程资产的已发布版本。 */
@Data
@AllArgsConstructor
public class FlowAssetVersionResp {
    private long id;
    private String flowCode;
    private long versionNumber;
    private String chainId;
}
