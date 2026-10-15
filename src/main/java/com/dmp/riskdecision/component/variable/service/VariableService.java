package com.dmp.riskdecision.component.variable.service;

import com.dmp.riskdecision.component.variable.model.VariablePage;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import java.util.List;
import java.util.Map;

/** 管理不可变变量版本并对决策流程提供单值取值。 */
public interface VariableService {
    /** 创建变量首个版本；变量码重复或来源配置无效时拒绝。 */
    VariableVersion create(String code, String name, VariableValueType valueType, String sourceType,
                           String url, int timeoutMs, List<String> parameters, String resultPath);

    /** 按预期版本号创建新版本，旧版本继续供已发布流程使用。 */
    VariableVersion update(String code, long expectedVersion, String name, VariableValueType valueType,
                           String sourceType, String url, int timeoutMs, List<String> parameters, String resultPath);

    /** 返回可供草稿选择的当前变量版本。 */
    VariableVersion current(String code);

    /** 后台按变量码或名称分页搜索当前版本。 */
    VariablePage search(String keyword, int page, int pageSize);

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
}
