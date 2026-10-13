package com.dmp.riskdecision.policy.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 场景列表分页结果。 */
@Getter
@AllArgsConstructor
public class ScenePage {
    private final List<SceneInfo> records;
    private final long total;
    private final int page;
    private final int pageSize;
}
