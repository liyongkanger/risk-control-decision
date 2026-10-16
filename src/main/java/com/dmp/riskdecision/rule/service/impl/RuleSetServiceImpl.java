package com.dmp.riskdecision.rule.service.impl;

import com.dmp.riskdecision.rule.mapper.RuleSetMapper;
import com.dmp.riskdecision.rule.mapper.RuleSetRow;
import com.dmp.riskdecision.rule.mapper.RuleVersionMapper;
import com.dmp.riskdecision.rule.mapper.RuleVersionRow;
import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleCondition;
import com.dmp.riskdecision.rule.model.RuleInput;
import com.dmp.riskdecision.rule.model.RuleRow;
import com.dmp.riskdecision.rule.model.RuleEvaluation;
import com.dmp.riskdecision.rule.model.RuleSetPage;
import com.dmp.riskdecision.rule.model.RuleSetSnapshot;
import com.dmp.riskdecision.rule.model.RuleSetSummary;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.service.RuleEvaluator;
import com.dmp.riskdecision.rule.service.RuleDefinitionValidator;
import com.dmp.riskdecision.rule.service.RuleSetService;
import com.dmp.riskdecision.rule.service.RuleVariableBindingService;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 在当前应用数据库中维护规则草稿与不可变版本。 */
@Service
public class RuleSetServiceImpl implements RuleSetService {
    @Resource private RuleSetMapper sets;
    @Resource private RuleVersionMapper versions;
    @Resource private RuleEvaluator evaluator;
    @Resource private RuleDefinitionValidator validator;
    @Resource private RuleVariableBindingService bindings;
    private final ObjectMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    /** {@inheritDoc} */
    @Override
    public RuleSetSnapshot create(String code, String name, RuleDefinition definition) {
        requireIdentity(code, name);
        validator.requireValid(definition);
        bindings.requireValid(definition, false);
        normalizeDecimals(definition);
        RuleSetRow row = new RuleSetRow();
        row.setRuleSetCode(code);
        row.setRuleSetName(name.trim());
        row.setDraftRevision(1L);
        row.setDraftJson(json.writeValueAsString(definition));
        row.setIsactive(1);
        try { sets.insert(row); }
        catch (DuplicateKeyException error) { throw new IllegalStateException("Rule set already exists", error); }
        return snapshot(row);
    }

    /** {@inheritDoc} */
    @Override
    public RuleSetSnapshot updateDraft(String code, long expectedRevision, RuleDefinition definition) {
        validator.requireValid(definition);
        bindings.requireValid(definition, false);
        normalizeDecimals(definition);
        int changed = sets.saveDraft(code, expectedRevision, json.writeValueAsString(definition));
        if (changed != 1) {
            get(code);
            throw new IllegalStateException("Rule draft revision conflict");
        }
        return get(code);
    }

    /** {@inheritDoc} 先写不可变版本，再以旧指针与修订号条件切换；事务保证失败时不留下半成品。 */
    @Transactional
    @Override
    public RuleSetVersion publish(String code, long expectedRevision) {
        RuleSetRow current = requireRow(code);
        if (current.getDraftRevision() != expectedRevision
                || current.getPublishedRevision() != null
                && current.getPublishedRevision() >= expectedRevision) {
            throw new IllegalStateException("Rule draft revision conflict");
        }
        RuleDefinition definition = parse(current.getDraftJson());
        validator.requireValid(definition);
        bindings.requireValid(definition, true);
        RuleVersionRow next = new RuleVersionRow();
        next.setRuleSetCode(code);
        next.setVersionNumber(versions.maxVersion(code) + 1);
        next.setDefinitionJson(json.writeValueAsString(definition));
        next.setIsactive(1);
        try { versions.insert(next); }
        catch (DuplicateKeyException error) { throw new IllegalStateException("Rule version conflict", error); }
        if (sets.pointVersion(code, expectedRevision, current.getCurrentVersionId(), next.getId()) != 1) {
            throw new IllegalStateException("Rule publish pointer conflict");
        }
        return convert(next);
    }

    /** {@inheritDoc} */
    @Override
    public RuleSetSnapshot get(String code) { return snapshot(requireRow(code)); }

    /** {@inheritDoc} */
    @Override
    public RuleSetPage search(String keyword, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        String pattern = "%" + (keyword == null ? "" : keyword.trim().replace("%", "\\%")) + "%";
        return new RuleSetPage(sets.search(pattern, (long) (page - 1) * pageSize, pageSize)
                .stream().map(row -> new RuleSetSummary(row.getRuleSetCode(), row.getRuleSetName(),
                        row.getDraftRevision(), row.getCurrentVersionId())).toList(),
                sets.count(pattern), page, pageSize);
    }

    /** {@inheritDoc} */
    @Override
    public RuleSetVersion currentVersion(String code) {
        RuleSetRow row = requireRow(code);
        if (row.getCurrentVersionId() == null) throw new NoSuchElementException("Rule set is not published: " + code);
        return requireVersion(row.getCurrentVersionId());
    }

    /** {@inheritDoc} */
    @Override
    public RuleSetVersion requireVersion(long id) {
        RuleVersionRow row = versions.selectById(id);
        if (row == null) throw new NoSuchElementException("Rule version not found: " + id);
        return convert(row);
    }

    /** {@inheritDoc} */
    @Override
    public RuleEvaluation evaluateVersion(long id, Map<String, Object> input) {
        return evaluateVersion(id, input, Map.of(), Map.of());
    }

    /** {@inheritDoc} */
    @Override
    public RuleEvaluation evaluateVersion(long id, Map<String, Object> input,
                                          Map<String, Object> nodeResults,
                                          Map<String, FlowContextBinding> contextBindings) {
        RuleSetVersion version = requireVersion(id);
        return evaluator.evaluate(version.getDefinition(), version.getCode(), id,
                bindings.resolve(version.getDefinition(), input, nodeResults, contextBindings));
    }

    /** {@inheritDoc} */
    @Override
    public RuleEvaluation testDraft(String code, Map<String, Object> input) {
        return testDraft(code, input, Map.of());
    }

    /** {@inheritDoc} */
    @Override
    public RuleEvaluation testDraft(String code, Map<String, Object> input,
                                    Map<String, Object> contextValues) {
        RuleSetRow row = requireRow(code);
        RuleDefinition definition = parse(row.getDraftJson());
        validator.requireValid(definition);
        bindings.requireValid(definition, false);
        return evaluator.evaluate(definition, code, 0,
                bindings.resolveForTest(definition, input, contextValues));
    }

    /** {@inheritDoc} */
    @Override
    public RuleEvaluation testVersion(long id, Map<String, Object> input,
                                      Map<String, Object> contextValues) {
        RuleSetVersion version = requireVersion(id);
        return evaluator.evaluate(version.getDefinition(), version.getCode(), id,
                bindings.resolveForTest(version.getDefinition(), input, contextValues));
    }

    private RuleSetRow requireRow(String code) {
        RuleSetRow row = sets.byCode(code);
        if (row == null) throw new NoSuchElementException("Rule set not found: " + code);
        return row;
    }

    private RuleSetSnapshot snapshot(RuleSetRow row) {
        return new RuleSetSnapshot(row.getRuleSetCode(), row.getRuleSetName(), row.getDraftRevision(),
                parse(row.getDraftJson()), row.getCurrentVersionId());
    }

    private RuleSetVersion convert(RuleVersionRow row) {
        return new RuleSetVersion(row.getId(), row.getRuleSetCode(), row.getVersionNumber(),
                parse(row.getDefinitionJson()));
    }

    private RuleDefinition parse(String source) {
        RuleDefinition definition = json.readValue(source, RuleDefinition.class);
        normalizeDecimals(definition);
        return definition;
    }

    /** 输出给浏览器的小数常量使用十进制文本，避免 JSON.parse 转成不精确的 JS Number。 */
    private void normalizeDecimals(RuleDefinition definition) {
        Map<String, String> types = new HashMap<>();
        for (RuleInput input : definition.getInputs()) types.put(input.getName(), input.getType());
        for (RuleRow row : definition.getRows()) {
            for (RuleCondition condition : row.getConditions()) {
                if ("DECIMAL".equals(types.get(condition.getInput()))
                        && condition.getExpected() instanceof Number) {
                    condition.setExpected(new BigDecimal(condition.getExpected().toString()).toPlainString());
                }
            }
        }
    }

    private void requireIdentity(String code, String name) {
        if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")
                || name == null || name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("Invalid rule set identity");
        }
    }

}
