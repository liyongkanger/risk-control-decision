package com.dmp.riskdecision.policy.repository;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.mapper.FlowDraftMapper;
import com.dmp.riskdecision.policy.mapper.FlowVersionMapper;
import com.dmp.riskdecision.policy.mapper.SceneMapper;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowVersion;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** 场景草稿与流程版本的持久化适配。 */
@Repository
public class SceneFlowRepository {
    @Resource
    private SceneMapper scenes;
    @Resource
    private FlowDraftMapper drafts;
    @Resource
    private FlowVersionMapper versions;
    @Resource
    private ObjectMapper json;

    public FlowDraft saveDraft(String sceneCode, GraphDefinition graph, long expectedRevision) {
        String encoded = json.writeValueAsString(graph);
        int changed;
        if (expectedRevision == 0) {
            try {
                changed = drafts.insert(sceneCode, encoded);
            } catch (org.springframework.dao.DuplicateKeyException conflict) {
                throw new IllegalStateException("Draft revision conflict", conflict);
            }
        } else {
            changed = drafts.update(sceneCode, encoded, expectedRevision);
        }
        if (changed != 1) throw new IllegalStateException("Draft revision conflict");
        return new FlowDraft(sceneCode, graph, expectedRevision + 1);
    }

    public Optional<FlowDraft> findDraft(String sceneCode) {
        FlowDraftMapper.DraftRow row = drafts.find(sceneCode);
        return row == null ? Optional.empty() : Optional.of(new FlowDraft(sceneCode,
                json.readValue(row.getGraphJson(), GraphDefinition.class), row.getRevision()));
    }

    public long nextVersionNumber(String sceneCode) {
        return versions.latestVersionNumber(sceneCode) + 1;
    }

    @org.springframework.transaction.annotation.Transactional
    public FlowVersion createVersion(String sceneCode, long number, GraphDefinition graph,
                                     String chainId, String compiledEl) {
        versions.insert(sceneCode, number, json.writeValueAsString(graph), chainId, compiledEl);
        return findVersionByNumber(sceneCode, number).orElseThrow();
    }

    public Optional<FlowVersion> findVersion(long id) {
        return Optional.ofNullable(versions.find(id)).map(this::convert);
    }

    public Optional<FlowVersion> findVersionByNumber(String sceneCode, long number) {
        return Optional.ofNullable(versions.findByNumber(sceneCode, number)).map(this::convert);
    }

    public Optional<FlowVersion> findPublished(String sceneCode) {
        Long id = scenes.publishedVersionId(sceneCode);
        return id == null ? Optional.empty() : findVersion(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public void switchPublished(String sceneCode, Long expectedId, long newId, long expectedRevision) {
        scenes.insertIfMissing(sceneCode);
        if (scenes.switchPublished(sceneCode, expectedId, newId, expectedRevision) != 1) {
            throw new IllegalStateException("Published version or draft revision conflict");
        }
    }

    public List<FlowVersion> findAllPublished() {
        return versions.findAllPublished().stream().map(this::convert).toList();
    }

    private FlowVersion convert(FlowVersionMapper.VersionRow row) {
        return new FlowVersion(row.getId(), row.getSceneCode(), row.getVersionNumber(),
                json.readValue(row.getGraphJson(), GraphDefinition.class), row.getChainId(), row.getCompiledEl());
    }
}
