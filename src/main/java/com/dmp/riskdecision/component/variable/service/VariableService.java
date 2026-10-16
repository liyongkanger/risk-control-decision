package com.dmp.riskdecision.component.variable.service;

import com.dmp.riskdecision.component.variable.model.VariablePage;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import java.util.List;
import java.util.Map;

/** 管理不可变变量版本并对决策流程提供单值取值。 */
public interface VariableService {
    /** 创建变量首个版本；变量码重复或来源配置无效时拒绝。 */
    VariableVersion create(String code, String name, VariableValueType valueType, String sourceType,
                           String url, int timeoutMs, List<String> parameters, String resultPath);

    /** 创建变量首版，并将外部来源的每个参数固定到指定变量版本。 */
    VariableVersion create(String code, String name, VariableValueType valueType, String sourceType,
                           String url, int timeoutMs, List<String> parameters, String resultPath,
                           Map<String, Long> dependencyVersionIds);

    /** 创建首版并保存逻辑变量分类；省略分类时使用其他。 */
    VariableVersion create(String code, String name, VariableValueType valueType, String sourceType,
                           String url, int timeoutMs, List<String> parameters, String resultPath,
                           Map<String, Long> dependencyVersionIds, String categoryCode);

    /** 按预期版本号创建新版本，旧版本继续供已发布流程使用。 */
    VariableVersion update(String code, long expectedVersion, String name, VariableValueType valueType,
                           String sourceType, String url, int timeoutMs, List<String> parameters, String resultPath);

    /** 创建新版本，并将外部来源依赖固定到指定变量版本。 */
    VariableVersion update(String code, long expectedVersion, String name, VariableValueType valueType,
                           String sourceType, String url, int timeoutMs, List<String> parameters, String resultPath,
                           Map<String, Long> dependencyVersionIds);

    /** 返回可供草稿选择的当前变量版本。 */
    VariableVersion current(String code);

    /** 后台按变量码或名称分页搜索当前版本。 */
    VariablePage search(String keyword, int page, int pageSize);

    /** 按分类、来源和值类型筛选当前版本。 */
    VariablePage search(String keyword, String categoryCode, String sourceType, String valueType,
                        int page, int pageSize);

    /** 只更新逻辑变量分类，不生成取值版本。 */
    VariableVersion updateCategory(String code, String categoryCode);

    /** 按固定 ID 读取不可变变量版本。 */
    VariableVersion requireVersion(long id);

    /**
     * 使用固定变量版本取一个强类型值；取值、路径或类型错误均抛异常。
     *
     * @param versionId 流程发布时固定的变量版本 ID
     * @param code 用于核对版本归属的变量码
     * @param parameters 与版本契约一致的来源查询参数
     * @return String、Boolean 或 BigDecimal 单值
     */
    Object resolve(long versionId, String code, Map<String, Object> parameters);

    /**
     * 在一次流程中按固定版本递归解析变量，所有请求值与节点结果均只属于本次调用。
     *
     * @throws VariableResolutionException 缺值、类型不符、依赖环或来源失败时抛出
     */
    Object resolveInFlow(long versionId, String code, Map<String, Object> requestInput,
                         Map<String, Object> nodeResults, Map<String, FlowContextBinding> bindings);

    /** 同一次规则求值复用解析缓存，避免共享外部变量重复取值。 */
    Object resolveInFlow(long versionId, String code, Map<String, Object> requestInput,
                         Map<String, Object> nodeResults, Map<String, FlowContextBinding> bindings,
                         Map<Long, Object> cache);

    /** 独立试算显式提供临时上下文样例；正式流程取值不允许样例回退。 */
    Object resolveForTest(long versionId, String code, Map<String, Object> requestInput,
                          Map<String, Object> contextSamples);

    /** 独立试算中的多个规则输入共享同一解析缓存。 */
    Object resolveForTest(long versionId, String code, Map<String, Object> requestInput,
                          Map<String, Object> contextSamples, Map<Long, Object> cache);
}
