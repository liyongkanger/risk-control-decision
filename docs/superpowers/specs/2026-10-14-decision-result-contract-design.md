# 决策业务结果契约设计

日期：2026-10-14
状态：已确认并实施；非空结果由 T2 的 `RESULT` 节点产出，验收以代码和测试为准。

## 目标与依据

在现有同步执行接口中明确区分**流程运行状态**与**业务决策结果**。营销样板可以返回“不新增优惠”“低额券”或“高额券”及原因；其他业务可定义自己的结论，不被营销或风控枚举限制。

依据：[v0.1 决策子流程 PRD](../../../prd/iterations/2026-10-v0.1-decision-subflow/prd.md)、[结算页优惠样板](../../../prd/scenarios/checkout-offer-incremental-profit.md)和[前后端协作规范](../../standards/frontend-backend-collaboration.md)。

## 现状与约束

- `POST /api/decisions/evaluate` 使用 `ApiResponse<EvaluateDecisionResp>`；`data` 已有 `requestId`、`sceneCode`、`versionId`、`status`、`nodeResults`、`trace`、`errorCode`。
- `status` 目前为 `SUCCEEDED` 或 `FAILED`，表示执行状态。`nodeResults` 是按节点 ID 保存的运行数据，不能直接当作最终业务结论。
- 已发布的演示流程没有业务结果节点或结果配置。用户已确认这些版本继续运行；其响应中的 `businessResult` 为 `null`。
- 当前只有场景入口的执行接口；独立流程的试运行和子流程调用由后续任务设计。T1 不改变请求路径、请求体及已有响应字段。

## 契约选择

采用**在现有响应中增加嵌套 `businessResult`** 的方式。保留原接口和旧字段，使现有页面及调用方仍能读取运行状态与节点结果。相较于另建 v2 接口，此方案避免两套同步执行契约；相较于把某个 `nodeResults` 值当作结论，此方案能让外层结果与子流程节点结果明确分离。

```text
ApiResponse.data
├── requestId: string
├── sceneCode: string
├── versionId: number                 # 本次固定选中的外层发布版本 ID
├── status: "SUCCEEDED" | "FAILED"  # 运行状态
├── businessResult: object | null    # 本流程明确产出的业务结果
├── nodeResults: object              # 运行数据；不是最终结论
├── trace: array
└── errorCode: string | null

businessResult
├── conclusion: string               # 本流程声明的业务结论代码
├── reasonCode: string               # 稳定的业务原因代码
├── reason: string                   # 可读的业务原因
├── details: object                  # 本流程定义的业务明细，默认 {}
└── suggestedAction: object | null   # 建议动作；不代表已执行

suggestedAction
├── code: string
└── parameters: object               # 默认 {}
```

`conclusion`、`reasonCode`、`reason` 以及建议动作存在时的 `suggestedAction.code` 均为非空字符串，由流程发布时声明并验证；平台不预设跨业务通用的结论枚举。`details` 和 `parameters` 使用 JSON 对象，具体字段由对应流程声明的结果配置约束，不允许调用方依赖某个节点的私有输出结构。`reason` 是展示文案，不作为程序分支条件。

## 状态与空值语义

| 情况 | `status` | `businessResult` | `errorCode` | HTTP 与外壳 |
| --- | --- | --- | --- | --- |
| 新版流程正常返回“无优惠” | `SUCCEEDED` | 非空，`conclusion=NO_NEW_OFFER` | `null` | 沿用 HTTP 200、`code=SUCCESS` |
| 新版流程正常推荐一档券 | `SUCCEEDED` | 非空，含建议优惠 | `null` | 沿用 HTTP 200、`code=SUCCESS` |
| 已发布的旧演示版本正常完成 | `SUCCEEDED` | `null` | `null` | 沿用 HTTP 200、`code=SUCCESS` |
| 流程执行失败 | `FAILED` | `null` | 稳定执行错误码 | 沿用当前 HTTP 200、`code=SUCCESS`，由 `status` 表示执行失败 |
| 场景不存在、尚未发布或请求无效 | 无执行结果 | 无执行结果 | 由错误响应表达 | 沿用现有全局异常处理与对应 HTTP 状态 |

`NO_NEW_OFFER`、`LOW_VALUE_OFFER`、`HIGH_VALUE_OFFER` 是营销样板的流程结论代码，不是平台固定枚举。执行失败时即使部分节点已有输出，`businessResult` 仍须为 `null`；`nodeResults` 与 `trace` 可保留已执行部分，供排查使用。旧版成功且 `businessResult=null` 表示“该版本没有业务结果契约”，不得显示为“无优惠”。

构造领域执行结果时也要保持此不变式：`status=FAILED` 不能携带非空 `businessResult`；`status=SUCCEEDED` 可以因旧发布版本而暂时没有业务结果。

## 营销样板响应示例

以下示例展示已实现的响应字段形态；具体结论取决于已发布 `RESULT` 节点的配置。`nodeResults` 和 `trace` 在实际接口中仍保留。

```json
{
  "code": "SUCCESS",
  "message": "成功",
  "data": {
    "requestId": "checkout-001",
    "sceneCode": "checkoutOffer",
    "versionId": 42,
    "status": "SUCCEEDED",
    "businessResult": {
      "conclusion": "LOW_VALUE_OFFER",
      "reasonCode": "ELIGIBLE_LOW_GROUP",
      "reason": "符合活动条件，建议低额优惠",
      "details": { "offerCode": "LOW_5_OFF_100" },
      "suggestedAction": {
        "code": "APPLY_OFFER",
        "parameters": { "offerCode": "LOW_5_OFF_100" }
      }
    },
    "nodeResults": {},
    "trace": [],
    "errorCode": null
  }
}
```

“建议优惠”只描述平台希望结算系统采取的处理；实际用券、支付和退款结果不写入本次 `businessResult`，由后续反馈能力记录。

## 分层与落地顺序

1. T1 在 `decision.model` 定义受控的业务结果对象，在 `decision.vo.resp` 增加对应 HTTP 响应对象，由 `DecisionAssembler` 映射；`EvaluateDecisionResp` 增加可空字段。`common.response.ApiResponse` 保持不变。
2. T1 在 `docs/api/` 新增决策结果接口文档，实施前标为“草案”；代码落地且测试核对后再标为“已实现”。前端消费契约前核对字段、空值和示例。
3. T2 设计显式结果产出与发布校验。届时新发布的决策流程每条可执行结束路径都必须给出一个业务结果；已有不可变发布版本维持旧行为，重新发布草稿须满足新规则。
4. T3/T4 进一步定义子流程结果在外层节点下的命名空间、输入映射与版本追溯。子流程的结果不自动覆盖外层 `businessResult`。

T1 的数据对象和响应扩展不引入数据库表，也不改变 LiteFlow 节点执行；实际结果何时写入执行上下文及如何从多分支中确定唯一结果由 T2 设计。

## 验收与风险

- 旧演示流程执行响应保留所有旧字段，新增 `businessResult: null`；现有页面读取 `status`、`versionId`、`nodeResults` 的行为不受影响。
- 业务结果对象能表达营销三种正常结论和其他业务定义的结论，并将建议动作与实际动作区分。
- 失败结果没有正常业务结论；调用方可凭 `status` 和 `errorCode` 区分运行失败与正常“不新增优惠”。
- `details` 与 `parameters` 是扩展点，若不在发布时按流程声明校验，前端会再次依赖临时字段；T2 必须给出明确的配置及校验方式。
- 旧版成功响应的空结果需要在页面显式标注为“旧版流程未定义业务结果”，避免误读。
