package com.dmp.riskdecision.policy.assembler;

import com.dmp.riskdecision.policy.model.SceneInfo;
import com.dmp.riskdecision.policy.model.ScenePage;
import com.dmp.riskdecision.policy.vo.resp.PageSceneResp;
import com.dmp.riskdecision.policy.vo.resp.SceneResp;
import org.springframework.stereotype.Component;

/** 场景业务结果到 HTTP 响应对象的转换。 */
@Component
public class SceneAssembler {
    public SceneResp toResp(SceneInfo scene) {
        return new SceneResp(scene.getSceneCode(), scene.getSceneName(), scene.getFlowCode(), scene.getDescription(),
                scene.getPublishedVersionNumber(), scene.getUpdatetime());
    }

    public PageSceneResp toPageResp(ScenePage page) {
        return new PageSceneResp(page.getRecords().stream().map(this::toResp).toList(),
                page.getTotal(), page.getPage(), page.getPageSize());
    }
}
