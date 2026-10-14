# 显式业务结果节点设计

日期：2026-10-14
状态：已确认并实施；验收以代码和测试结果为准。

## 目标与范围

让新发布的决策流程在每条可执行结束路径上**恰好产出一个业务结果**。业务结果由独立 `RESULT` 节点明确配置，执行成功时填入 T1 的 `businessResult`；流程运行失败时仍为 `null`。此设计以[v0.1 PRD](../../../prd/iterations/2026-10-v0.1-decision-subflow/prd.md)、[结算页优惠样板](../../../prd/scenarios/checkout-offer-incremental-profit.md)和[T1 结果契约](2026-10-14-decision-result-contract-design.md)为依据。

本轮先实现后端图校验、编译、运行和接口文档。画布配置体验属于后续前端接入任务；子流程调用、输入映射和效果反馈也不在 T2 内。

## 已确定的表示方式

新增 `NodeKind.RESULT`。它是业务结果语义节点，与 `START`、`END` 等同为图结构的一部分；不通过普通 `COMPONENT` 的 `componentKey` 暗示最终结论。节点必须有一条入线和一条出线，`componentKey`、`failurePolicy` 均为空，结果内容放在 `config` 中。草稿可以暂缺或暂未填完结果节点；发布时校验完整性与路径覆盖。

```json
{
  "id": "lowOfferResult",
  "kind": "RESULT",
  "componentKey": null,
  "failurePolicy": null,
  "config": {
    "conclusion": "LOW_VALUE_OFFER",
    "reasonCode": "ELIGIBLE_LOW_GROUP",
    "reason": "符合活动条件，建议低额优惠",
    "details": { "offerCode": "LOW_5_OFF_100" },
    "suggestedAction": {
      "code": "APPLY_OFFER",
      "parameters": { "offerCode": "LOW_5_OFF_100" }
    }
  }
}
```

`conclusion`、`reasonCode`、`reason` 为非空字符串；`details`、`suggestedAction.parameters` 为 JSON 对象，省略时按 `{}`；`suggestedAction` 可为空，存在时 `code` 非空。发布时拒绝未知字段或错误类型，使结果对象与 T1 响应契约一致。T2 只接受配置中的常量 JSON 值，不在配置里解释表达式或读取任意节点私有字段。营销样板通过条件分支选中不同的 `RESULT` 节点；子流程结果参与条件判断留给 T3/T4。

## 结构和路径语义

保持现有**唯一 `START`、唯一 `END`** 的图约定。`RESULT` 可以放在串行路径或条件分支中；条件分支仍在现有 `CONDITION_JOIN` 汇合。一次运行只执行命中的条件分支，因此两条互斥分支可以分别放一个 `RESULT`。`RESULT` 后允许继续经过汇合与 `END`，但不得再经过第二个 `RESULT`。

并行分支会同时执行，T2 禁止将 `RESULT` 放在 `PARALLEL_SPLIT` 与其对应 `PARALLEL_JOIN` 之间，也禁止在结果产生后再次进入并行拆分。需要并行数据时，先汇合，再通过条件或串行路径确定唯一结果。这个边界防止两个分支争写同一最终结论；以后若需并行结果聚合，应另行设计明确规则。

发布校验在现有图结构和编译校验通过后，对从 `START` 到 `END` 的所有可执行条件路径计算结果节点数量：

- 某条路径为 0 个：返回定位到遗漏分支或 `END` 的 `RESULT_MISSING`，阻止发布。
- 某条路径超过 1 个：返回定位到重复节点的 `RESULT_DUPLICATE`，阻止发布。
- 结果位于并行区域或后面又进入并行拆分：返回定位到结果节点的 `RESULT_PARALLEL_UNSUPPORTED`。
- 配置缺少必填值、字段类型错误或含未知字段：返回定位到该节点的 `RESULT_CONFIG_INVALID`。

验证器可以按已验证的无环图做拓扑传播：在每个节点维护可能的结果计数集合，计数超过 1 时统一截断为 2；条件分支保留不同可能值，并行区域先按上述约束排除结果写入。到达 `END` 时集合必须仅为 `{1}`。发布校验和 `/validate` 使用同一规则。图规模仍受现有 200 节点、500 连线、1 MiB 上限约束，无须枚举指数级的完整路径。

## 编译与运行

`FlowCompiler` 将 `RESULT` 编译成平台专用的 LiteFlow 执行节点，并把图节点 ID 保留为 tag；它不通过组件目录查找业务组件。运行前将 `RESULT` 的配置与其他执行节点配置一同传入本次执行上下文。专用适配器将本次选中的配置写入上下文的**单个结果槽**并记录节点轨迹；第二次写入直接失败，作为发布校验之外的运行时保护。

LiteFlow 组件包只传递结果配置的 JSON 数据，不依赖 `decision.model.BusinessResult`。执行服务在链成功后把结果配置转换为 T1 的受控 `BusinessResult` 并返回；链失败时清空业务结果，保留已有节点结果与轨迹。对含 `RESULT` 的新版本，链报告成功但结果槽仍为空属于运行异常，返回失败状态和稳定错误码；不把它解释为正常“不新增优惠”。已有不可变版本不含 `RESULT`，继续成功执行并返回 `businessResult: null`。

发布流程当前会用示例输入试运行生成的链。T2 仍保留此检查，但不依赖一次样例运行覆盖所有条件路径；静态路径校验承担全路径完整性检查，试运行只验证已选路径能够加载和执行。

## 旧图与前后端交接

已有不可变发布版本和场景指针保持不变，运行时按 T1 兼容语义返回空业务结果。**新发布或重新发布**的决策草稿都必须满足 `RESULT` 校验，包括之前只含 `emit-value` 的示例图。现有后端测试与前端示例模板需要补上适当的结果节点；这属于迁移演示资产，不修改历史 Flyway 数据或已发布版本。

后端完成后更新 `docs/api/`：记录 `RESULT` 图节点配置、校验错误码、执行成功和执行失败示例，并核对 T1 的[同步决策接口文档](../../api/decision-results.md)。前端接入前再按[前后端协作规范](../../standards/frontend-backend-collaboration.md)确认字段、空值和配置行为。

## 验收

1. 串行图含一个 `RESULT` 时可发布，执行返回配置的结论、原因、明细和可选建议动作。
2. 条件分支各有一个结果时，两种输入分别得到对应结论；缺一支、同一路径两个结果、并行区域写结果均给出可定位问题并阻止发布。
3. 草稿可暂时不完整；`/validate` 与 `/publish` 对同一完整图使用一致规则。
4. 旧已发布流程继续执行且 `businessResult=null`；重新发布旧草稿必须补结果。
5. 组件失败或结果运行异常返回 `FAILED` 且 `businessResult=null`，不生成正常优惠结论。

## 风险与后续设计点

- `RESULT` 的常量明细适合首个营销样板；若业务明细需引用上游计算值，须在后续任务定义受控字段映射，不把任意表达式注入 JSON 配置。
- 条件节点目前只读请求输入。要让外层根据子流程结果选择结论，T3/T4 需扩展受控的结果引用机制。
- 当前示例图与测试大量省略业务结果。实施计划必须列出需要更新的测试和模板，避免只改后端验证器导致现有演示流程无法重新发布。
