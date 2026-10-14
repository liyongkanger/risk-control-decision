package com.dmp.riskdecision.policy.repository;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.mapper.FlowDraftMapper;
import com.dmp.riskdecision.policy.mapper.FlowDraftRow;
import com.dmp.riskdecision.policy.mapper.FlowMapper;
import com.dmp.riskdecision.policy.mapper.FlowRow;
import com.dmp.riskdecision.policy.mapper.FlowVersionMapper;
import com.dmp.riskdecision.policy.mapper.FlowVersionRow;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowInfo;
import com.dmp.riskdecision.policy.model.FlowPage;
import com.dmp.riskdecision.policy.model.FlowVersion;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 独立流程资产、草稿及不可变版本的持久化适配。 */
@Repository
public class FlowAssetRepository {
    @Resource
    private FlowMapper flows;
    @Resource
    private FlowDraftMapper drafts;
    @Resource
    private FlowVersionMapper versions;
    @Resource
    private ObjectMapper json;

    /** 创建未绑定场景的决策流程。 */
    public FlowInfo create(String flowCode, String flowName) {
        FlowRow row = new FlowRow();
        row.setFlowCode(flowCode);
        row.setFlowName(flowName);
        row.setFlowType("DECISION");
        flows.insert(row);
        return require(flowCode);
    }

    public FlowInfo require(String flowCode) {
        FlowRow row = flows.findByCode(flowCode);
        if (row == null) throw new NoSuchElementException("Flow not found: " + flowCode);
        return toInfo(row);
    }

    public FlowInfo require(long flowId) {
        FlowRow row = flows.findById(flowId);
        if (row == null) throw new NoSuchElementException("Flow not found: " + flowId);
        return toInfo(row);
    }

    public FlowPage search(String pattern, int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        List<FlowInfo> records = flows.search(pattern, offset, pageSize).stream()
                .map(this::toInfo).toList();
        return new FlowPage(records, flows.count(pattern), page, pageSize);
    }

    public FlowDraft saveDraft(String flowCode, GraphDefinition graph, long expectedRevision) {
        long flowId = require(flowCode).getId();
        String encoded = json.writeValueAsString(graph);
        int changed;
        if (expectedRevision == 0) {
            try {
                changed = drafts.insert(flowId, encoded);
            } catch (DuplicateKeyException error) {
                throw new IllegalStateException("Draft revision conflict", error);
            }
        } else {
            changed = drafts.update(flowId, encoded, expectedRevision);
        }
        if (changed != 1) throw new IllegalStateException("Draft revision conflict");
        return new FlowDraft(flowCode, graph, expectedRevision + 1);
    }

    public Optional<FlowDraft> findDraft(String flowCode) {
        long flowId = require(flowCode).getId();
        FlowDraftRow row = drafts.find(flowId);
        return row == null ? Optional.empty() : Optional.of(new FlowDraft(flowCode,
                json.readValue(row.getGraphJson(), GraphDefinition.class), row.getRevision()));
    }

    public long nextVersionNumber(String flowCode) {
        return versions.latestVersionNumber(require(flowCode).getId()) + 1;
    }

    @Transactional
    public FlowVersion createVersion(String flowCode, long number, GraphDefinition graph,
                                     String chainId, String compiledEl) {
        long flowId = require(flowCode).getId();
        versions.insert(flowId, number, json.writeValueAsString(graph), chainId, compiledEl);
        return findVersionByNumber(flowCode, number).orElseThrow();
    }

    public Optional<FlowVersion> findVersion(long id) {
        return Optional.ofNullable(versions.find(id)).map(this::convert);
    }

    public Optional<FlowVersion> findVersionByNumber(String flowCode, long number) {
        return Optional.ofNullable(versions.findByNumber(require(flowCode).getId(), number)).map(this::convert);
    }

    public Optional<FlowVersion> findPublished(String flowCode) {
        Long id = require(flowCode).getPublishedVersionId();
        return id == null ? Optional.empty() : findVersion(id);
    }

    @Transactional
    public void switchPublished(String flowCode, Long expectedId, long newId, long expectedRevision) {
        switchPublished(flowCode, expectedId, newId, expectedRevision, Map.of());
    }

    /** 锁定引用的子流程发布指针，再条件切换外层版本。 */
    @Transactional
    public void switchPublished(String flowCode, Long expectedId, long newId, long expectedRevision,
                                Map<String, Long> pinnedSubflows) {
        for (String childCode : pinnedSubflows.keySet().stream().sorted().toList()) {
            FlowRow child = flows.findByCodeForUpdate(childCode);
            if (child == null || !pinnedSubflows.get(childCode).equals(child.getPublishedVersionId())) {
                throw new IllegalStateException("Subflow published version changed: " + childCode);
            }
        }
        long flowId = require(flowCode).getId();
        if (flows.switchPublished(flowId, expectedId, newId, expectedRevision) != 1) {
            throw new IllegalStateException("Published version or draft revision conflict");
        }
    }

    public List<FlowVersion> findAllPublished() {
        return versions.findAllPublished().stream().map(this::convert).toList();
    }

    private FlowVersion convert(FlowVersionRow row) {
        return new FlowVersion(row.getId(), row.getFlowCode(), row.getVersionNumber(),
                json.readValue(row.getGraphJson(), GraphDefinition.class), row.getChainId(), row.getCompiledEl());
    }

    private FlowInfo toInfo(FlowRow row) {
        return new FlowInfo(row.getId(), row.getFlowCode(), row.getFlowName(), row.getFlowType(),
                row.getPublishedVersionId(), row.getPublishedVersionNumber(), row.getUpdatetime());
    }
}
