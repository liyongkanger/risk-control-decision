package com.dmp.riskdecision.policy.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dmp.riskdecision.policy.mapper.SceneMapper;
import com.dmp.riskdecision.policy.mapper.SceneRow;
import com.dmp.riskdecision.policy.mapper.FlowMapper;
import com.dmp.riskdecision.policy.mapper.FlowRow;
import com.dmp.riskdecision.policy.model.SceneInfo;
import com.dmp.riskdecision.policy.model.ScenePage;
import com.dmp.riskdecision.policy.service.SceneCodeConflictException;
import com.dmp.riskdecision.policy.service.SceneService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 场景基本信息的创建、更新与查询实现。 */
@Service
@Slf4j
public class SceneServiceImpl implements SceneService {
    @Resource
    private SceneMapper mapper;
    @Resource
    private FlowMapper flows;

    /** 创建有效场景，重复场景码返回独立业务冲突。 */
    @Override
    @Transactional
    public SceneInfo create(String sceneCode, String sceneName, String description) {
        log.info("[SceneServiceImpl][create] sceneCode:{}", sceneCode);
        validateSceneCode(sceneCode);
        String name = validateName(sceneName);
        String detail = normalizeDescription(description);
        if (mapper.findByCode(sceneCode) != null) {
            throw new SceneCodeConflictException(sceneCode, null);
        }
        FlowRow flow = flows.findByCode(sceneCode);
        if (flow == null) {
            flow = new FlowRow();
            flow.setFlowCode(sceneCode);
            flow.setFlowName(name);
            flow.setFlowType("DECISION");
            try {
                flows.insert(flow);
            } catch (DuplicateKeyException concurrentCreate) {
                flow = flows.findByCode(sceneCode);
                if (flow == null) throw concurrentCreate;
            }
        }
        SceneRow row = new SceneRow();
        row.setSceneCode(sceneCode);
        row.setSceneName(name);
        row.setDescription(detail);
        row.setFlowId(flow.getId());
        row.setPublishedVersionId(flow.getPublishedVersionId());
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException error) {
            throw new SceneCodeConflictException(sceneCode, error);
        }
        return get(sceneCode);
    }

    /** 绑定已有流程；重新绑定同一流程可显式选中其最新发布版本。 */
    @Override
    public SceneInfo bindFlow(String sceneCode, String flowCode) {
        log.info("[SceneServiceImpl][bindFlow] sceneCode:{} flowCode:{}", sceneCode, flowCode);
        validateSceneCode(sceneCode);
        if (flowCode == null || !flowCode.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid flow code");
        }
        FlowRow flow = flows.findByCode(flowCode);
        if (flow == null) throw new NoSuchElementException("Flow not found: " + flowCode);
        LambdaUpdateWrapper<SceneRow> update = new LambdaUpdateWrapper<SceneRow>()
                .eq(SceneRow::getSceneCode, sceneCode)
                .eq(SceneRow::getIsactive, 1)
                .set(SceneRow::getFlowId, flow.getId())
                .set(SceneRow::getPublishedVersionId, flow.getPublishedVersionId());
        if (mapper.update(null, update) != 1) {
            throw new NoSuchElementException("Scene not found: " + sceneCode);
        }
        return get(sceneCode);
    }

    /** 仅更新可编辑的名称与描述，场景码作为固定查找键。 */
    @Override
    public SceneInfo update(String sceneCode, String sceneName, String description) {
        log.info("[SceneServiceImpl][update] sceneCode:{}", sceneCode);
        validateSceneCode(sceneCode);
        String name = validateName(sceneName);
        String detail = normalizeDescription(description);
        LambdaUpdateWrapper<SceneRow> update = new LambdaUpdateWrapper<SceneRow>()
                .eq(SceneRow::getSceneCode, sceneCode)
                .eq(SceneRow::getIsactive, 1)
                .set(SceneRow::getSceneName, name)
                .set(SceneRow::getDescription, detail);
        if (mapper.update(null, update) != 1) {
            throw new NoSuchElementException("Scene not found: " + sceneCode);
        }
        return get(sceneCode);
    }

    /** 读取有效场景，包括当前发布版本号。 */
    @Override
    public SceneInfo get(String sceneCode) {
        validateSceneCode(sceneCode);
        SceneRow row = mapper.findByCode(sceneCode);
        if (row == null) {
            throw new NoSuchElementException("Scene not found: " + sceneCode);
        }
        return toInfo(row);
    }

    /** 按页搜索有效场景，页码从一开始。 */
    @Override
    public ScenePage search(String keyword, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid page or pageSize");
        }
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Keyword is too long");
        }
        String pattern = "%" + normalized.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
        long offset = ((long) page - 1) * pageSize;
        List<SceneInfo> records = mapper.search(pattern, offset, pageSize).stream()
                .map(this::toInfo).toList();
        return new ScenePage(records, mapper.count(pattern), page, pageSize);
    }

    private SceneInfo toInfo(SceneRow row) {
        return new SceneInfo(row.getSceneCode(), row.getSceneName(), row.getFlowCode(), row.getDescription(),
                row.getPublishedVersionNumber(), row.getUpdatetime());
    }

    private void validateSceneCode(String sceneCode) {
        if (sceneCode == null || !sceneCode.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid scene code");
        }
    }

    private String validateName(String sceneName) {
        String name = sceneName == null ? "" : sceneName.trim();
        if (name.isEmpty() || name.length() > 128) {
            throw new IllegalArgumentException("Scene name must contain 1 to 128 characters");
        }
        return name;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.length() > 512) {
            throw new IllegalArgumentException("Scene description exceeds 512 characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
