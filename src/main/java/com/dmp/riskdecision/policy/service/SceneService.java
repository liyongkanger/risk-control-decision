package com.dmp.riskdecision.policy.service;

import com.dmp.riskdecision.policy.model.SceneInfo;
import com.dmp.riskdecision.policy.model.ScenePage;

/** 场景基本信息管理用例。 */
public interface SceneService {
    /** 创建场景，场景码创建后不允许修改。 */
    SceneInfo create(String sceneCode, String sceneName, String description);

    /** 更新场景名称与描述。 */
    SceneInfo update(String sceneCode, String sceneName, String description);

    /** 将场景绑定到独立流程，并选中该流程当前发布版本。 */
    SceneInfo bindFlow(String sceneCode, String flowCode);

    /** 获取有效场景详情。 */
    SceneInfo get(String sceneCode);

    /** 按场景码或名称搜索有效场景。 */
    ScenePage search(String keyword, int page, int pageSize);
}
