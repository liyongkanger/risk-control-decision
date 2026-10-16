package com.dmp.riskdecision.rule.service;

import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleEvaluation;
import com.dmp.riskdecision.rule.model.RuleSetPage;
import com.dmp.riskdecision.rule.model.RuleSetSnapshot;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import java.util.Map;

/** 管理规则集草稿及不可变版本，并对流程提供纯计算规则执行。 */
public interface RuleSetService {
    /** 创建规则集及修订号为 1 的草稿。 */
    RuleSetSnapshot create(String code, String name, RuleDefinition definition);

    /** 在预期修订号匹配时保存草稿，旧发布版本保持不变。 */
    RuleSetSnapshot updateDraft(String code, long expectedRevision, RuleDefinition definition);

    /** 发布当前草稿为新版本，失败时不切换发布指针。 */
    RuleSetVersion publish(String code, long expectedRevision);

    /** 返回规则集草稿及当前发布指针。 */
    RuleSetSnapshot get(String code);

    /** 按关键词分页查询规则集。 */
    RuleSetPage search(String keyword, int page, int pageSize);

    /** 返回当前已发布版本；从未发布的规则集视为不可用。 */
    RuleSetVersion currentVersion(String code);

    /** 按固定 ID 读取不可变规则版本。 */
    RuleSetVersion requireVersion(long id);

    /** 对某个不可变版本和输入快照执行规则。 */
    RuleEvaluation evaluateVersion(long id, Map<String, Object> input);

    /** 在当前流程执行上下文中解析规则所引用的变量。 */
    RuleEvaluation evaluateVersion(long id, Map<String, Object> input,
                                   Map<String, Object> nodeResults,
                                   Map<String, FlowContextBinding> contextBindings);

    /** 用当前草稿和输入快照试算，不修改草稿或发布指针。 */
    RuleEvaluation testDraft(String code, Map<String, Object> input);

    /** 使用临时上下文样例试算草稿；样例不会写入变量中心。 */
    RuleEvaluation testDraft(String code, Map<String, Object> input,
                             Map<String, Object> contextValues);

    /** 使用临时上下文样例试算固定版本。 */
    RuleEvaluation testVersion(long id, Map<String, Object> input,
                               Map<String, Object> contextValues);
}
