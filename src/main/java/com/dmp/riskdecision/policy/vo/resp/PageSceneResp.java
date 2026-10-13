package com.dmp.riskdecision.policy.vo.resp;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 场景列表分页响应。 */
@Data
@AllArgsConstructor
public class PageSceneResp {
    private List<SceneResp> records;
    private long total;
    private int page;
    private int pageSize;
}
