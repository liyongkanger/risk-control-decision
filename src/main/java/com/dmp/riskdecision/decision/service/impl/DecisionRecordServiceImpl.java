package com.dmp.riskdecision.decision.service.impl;

import com.dmp.riskdecision.decision.assembler.DecisionAssembler;
import com.dmp.riskdecision.decision.mapper.DecisionRecordMapper;
import com.dmp.riskdecision.decision.mapper.DecisionRecordRow;
import com.dmp.riskdecision.decision.model.DecisionRecord;
import com.dmp.riskdecision.decision.model.DecisionRecordPage;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionRecordService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 基于数据库保存执行快照，详情读取时才解析大字段 JSON。 */
@Service
public class DecisionRecordServiceImpl implements DecisionRecordService {
    @Resource private DecisionRecordMapper mapper;
    @Resource private DecisionAssembler assembler;
    @Resource private ObjectMapper json;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public String save(ExecutionResult result, Map<String, Object> input) {
        DecisionRecordRow row = new DecisionRecordRow();
        row.setExecutionId(UUID.randomUUID().toString());
        row.setRequestId(result.getRequestId());
        row.setSceneCode(result.getSceneCode());
        row.setVersionId(result.getVersionId());
        row.setStatus(result.getStatus());
        row.setConclusion(result.getBusinessResult() == null ? null
                : result.getBusinessResult().getConclusion());
        row.setErrorCode(result.getErrorCode());
        row.setInputJson(json.writeValueAsString(input == null ? Map.of() : input));
        row.setResultJson(json.writeValueAsString(
                assembler.toEvaluateResp(result.withExecutionId(row.getExecutionId()))));
        mapper.insert(row);
        return row.getExecutionId();
    }

    /** {@inheritDoc} */
    @Override
    public DecisionRecord get(String executionId) {
        if (executionId == null || !executionId.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid executionId");
        }
        DecisionRecordRow row = mapper.findByExecutionId(executionId);
        if (row == null) throw new NoSuchElementException("Decision record not found: " + executionId);
        DecisionRecord record = summary(row);
        record.setInputJson(row.getInputJson());
        record.setResultJson(row.getResultJson());
        record.setInput(json.readValue(row.getInputJson(), new TypeReference<Map<String, Object>>() { }));
        record.setResult(json.readValue(row.getResultJson(), new TypeReference<Map<String, Object>>() { }));
        return record;
    }

    /** {@inheritDoc} */
    @Override
    public DecisionRecordPage search(String sceneCode, String requestId, String status, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid page or pageSize");
        }
        String scene = normalize(sceneCode, 64);
        String request = normalize(requestId, 191);
        String state = normalize(status, 16);
        if (state != null && !"SUCCEEDED".equals(state) && !"FAILED".equals(state)) {
            throw new IllegalArgumentException("Invalid decision status");
        }
        long offset = ((long) page - 1) * pageSize;
        List<DecisionRecord> records = mapper.search(scene, request, state, offset, pageSize)
                .stream().map(this::summary).toList();
        return new DecisionRecordPage(records, mapper.count(scene, request, state), page, pageSize);
    }

    private String normalize(String value, int limit) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > limit) throw new IllegalArgumentException("Filter is too long");
        return normalized;
    }

    private DecisionRecord summary(DecisionRecordRow row) {
        DecisionRecord record = new DecisionRecord();
        record.setExecutionId(row.getExecutionId());
        record.setRequestId(row.getRequestId());
        record.setSceneCode(row.getSceneCode());
        record.setVersionId(row.getVersionId());
        record.setStatus(row.getStatus());
        record.setConclusion(row.getConclusion());
        record.setErrorCode(row.getErrorCode());
        record.setCreatedAt(row.getInserttime());
        return record;
    }
}
