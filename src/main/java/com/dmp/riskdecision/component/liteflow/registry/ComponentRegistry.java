package com.dmp.riskdecision.component.liteflow.registry;

import com.dmp.riskdecision.component.liteflow.service.ComponentDefinition;
import java.util.Map;
import java.util.List;
import com.dmp.riskdecision.component.liteflow.service.ComponentConfigField;
import org.springframework.stereotype.Component;

/** LiteFlow 示例组件的内部注册表。 */
@Component
public class ComponentRegistry {
    // todo 后续优化不要用代码的方式写入map

    private static final Map<String, ComponentDefinition> DEFINITIONS = Map.of(
            "emit-value", new ComponentDefinition("emit-value", "emitValue", false, "输出固定值",
                    "把节点配置中的固定值写入节点结果，供演示流程验证连线。", "ANY",
                    List.of(field("value", "输出值", "ANY")), Map.of("value", "demo"), "DATA"),
            "is-positive", new ComponentDefinition("is-positive", "isPositive", true, "输入为真",
                    "读取流程输入；布尔值直接判断，数字大于零时进入 true 分支。", "BOOLEAN",
                    List.of(field("inputKey", "请求输入字段", "STRING")), Map.of("inputKey", "positive"), "CONDITION"),
            "equals-value", new ComponentDefinition("equals-value", "equalsValue", true, "上游结果等于指定值",
                    "读取已执行节点的结果路径，与预期文本比较并选择条件分支。", "BOOLEAN",
                    List.of(field("sourceNodeId", "上游节点 ID", "STRING"),
                            field("path", "结果路径", "STRING"), field("expected", "预期值", "STRING")),
                    Map.of("sourceNodeId", "", "path", "businessResult.conclusion", "expected", ""), "CONDITION"),
            "get-variable", new ComponentDefinition("get-variable", "getVariable", false, "获取变量",
                    "引用变量中心的固定版本，按变量依赖取值并输出一个强类型值。", "VARIABLE_VALUE",
                    List.of(field("variableCode", "变量码", "STRING"),
                            field("versionId", "固定版本 ID", "NUMBER")),
                    Map.of("variableCode", "", "versionId", 0), "DATA"),
            "compare-value", new ComponentDefinition("compare-value", "compareValue", true, "比较变量",
                    "比较上游变量值；文本和布尔值支持相等判断，十进制数还支持大小比较。", "BOOLEAN",
                    List.of(field("sourceNodeId", "取值节点 ID", "STRING"),
                            field("operator", "比较操作", "STRING"),
                            field("valueType", "变量类型", "STRING"),
                            field("expected", "预期值", "ANY")),
                    Map.of("sourceNodeId", "", "operator", "EQ", "valueType", "STRING", "expected", ""), "CONDITION"),
            "execute-rule-set", new ComponentDefinition("execute-rule-set", "executeRuleSet", false, "执行规则集",
                    "引用规则中心的固定发布版本；规则集绑定的变量由规则集取值。", "RULE_RESULT",
                    List.of(field("ruleSetCode", "规则集码", "STRING"),
                            field("ruleVersionId", "固定版本 ID", "NUMBER")),
                    Map.of("ruleSetCode", "", "ruleVersionId", 0), "RULE"),
            "rule-result-equals", new ComponentDefinition("rule-result-equals", "ruleResultEquals", true, "规则结果等于指定码",
                    "读取已执行规则节点的结果码，选择条件分支。", "BOOLEAN",
                    List.of(field("sourceNodeId", "规则节点 ID", "STRING"),
                            field("expectedResultCode", "预期结果码", "STRING")),
                    Map.of("sourceNodeId", "", "expectedResultCode", ""), "RULE"));

    private static ComponentConfigField field(String name, String label, String type) {
        return new ComponentConfigField(name, label, type, true);
    }

    public ComponentDefinition require(String componentKey) {
        ComponentDefinition definition = DEFINITIONS.get(componentKey);
        if (definition == null) throw new IllegalArgumentException("Unknown component: " + componentKey);
        return definition;
    }

    public List<ComponentDefinition> list() {
        return DEFINITIONS.values().stream().sorted(java.util.Comparator.comparing(ComponentDefinition::getKey)).toList();
    }
}
