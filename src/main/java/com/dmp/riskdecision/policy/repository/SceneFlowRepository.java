package com.dmp.riskdecision.policy.repository;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.mapper.SceneMapper;
import com.dmp.riskdecision.policy.mapper.SceneRow;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowVersion;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 将历史场景流程接口适配到独立流程资产。 */
@Repository
public class SceneFlowRepository {
    @Resource
    private SceneMapper scenes;
    @Resource
    private FlowAssetRepository flows;

    /** 流程操作只能引用已创建且绑定入口流程的有效场景。 */
    public void requireActiveScene(String sceneCode) {
        resolveFlowCode(sceneCode);
    }

    public String resolveFlowCode(String sceneCode) {
        SceneRow scene = scenes.findByCode(sceneCode);
        if (scene == null) throw new NoSuchElementException("Scene not found: " + sceneCode);
        return flows.require(scene.getFlowId()).getFlowCode();
    }

    public FlowDraft saveDraft(String sceneCode, GraphDefinition graph, long expectedRevision) {
        FlowDraft draft = flows.saveDraft(resolveFlowCode(sceneCode), graph, expectedRevision);
        return new FlowDraft(sceneCode, draft.getGraph(), draft.getRevision());
    }

    public Optional<FlowDraft> findDraft(String sceneCode) {
        return flows.findDraft(resolveFlowCode(sceneCode))
                .map(draft -> new FlowDraft(sceneCode, draft.getGraph(), draft.getRevision()));
    }

    public long nextVersionNumber(String sceneCode) {
        return flows.nextVersionNumber(resolveFlowCode(sceneCode));
    }

    @Transactional
    public FlowVersion createVersion(String sceneCode, long number, GraphDefinition graph,
                                     String chainId, String compiledEl) {
        FlowVersion version = flows.createVersion(resolveFlowCode(sceneCode), number, graph, chainId, compiledEl);
        return withSceneCode(sceneCode, version);
    }

    public Optional<FlowVersion> findVersion(long id) {
        return flows.findVersion(id);
    }

    public Optional<FlowVersion> findVersionByNumber(String sceneCode, long number) {
        return flows.findVersionByNumber(resolveFlowCode(sceneCode), number)
                .map(version -> withSceneCode(sceneCode, version));
    }

    public Optional<FlowVersion> findPublished(String sceneCode) {
        SceneRow scene = scenes.findByCode(sceneCode);
        if (scene == null) throw new NoSuchElementException("Scene not found: " + sceneCode);
        Long id = scene.getPublishedVersionId();
        return id == null ? Optional.empty() : flows.findVersion(id).map(version -> withSceneCode(sceneCode, version));
    }

    @Transactional
    public void switchPublished(String sceneCode, Long expectedId, long newId, long expectedRevision) {
        String flowCode = resolveFlowCode(sceneCode);
        Long flowPrevious = flows.require(flowCode).getPublishedVersionId();
        flows.switchPublished(flowCode, flowPrevious, newId, expectedRevision);
        if (scenes.switchPublished(sceneCode, expectedId, newId, expectedRevision) != 1) {
            throw new IllegalStateException("Published version or draft revision conflict");
        }
    }

    public List<FlowVersion> findAllPublished() {
        return flows.findAllPublished();
    }

    private FlowVersion withSceneCode(String sceneCode, FlowVersion version) {
        return new FlowVersion(version.getId(), sceneCode, version.getVersionNumber(), version.getGraph(),
                version.getChainId(), version.getCompiledEl());
    }
}
