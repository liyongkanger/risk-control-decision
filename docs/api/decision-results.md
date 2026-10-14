# 同步决策执行与业务结果接口

日期：2026-10-14
状态：T1/T2 后端已实现并通过 H2 测试；新发布流程的 `RESULT` 节点可产出非空业务结果，旧发布版本继续返回 `businessResult: null`。

## 用途与当前边界

`POST /api/decisions/evaluate` 根据场景当前选中的发布版本，同步执行一条决策流程。本次请求在开始执行时固定版本，期间发布指针切换不改变本次结果。当前只有场景入口的直接执行接口；外层流程中的 `SUBFLOW` 节点已能调用固定版本，独立流程直接试运行接口尚未实现。新发布流程必须配置显式业务结果，配置方式见[流程资产接口](flow-assets.md)。

接口继续使用统一的 `ApiResponse<T>` 外壳。旧发布版本没有业务结果定义，成功时返回 `businessResult: null`。这表示**该版本未定义业务结果**，不能解释为“不新增优惠”或其他正常业务结论。

## 请求

```http
POST /api/decisions/evaluate
Content-Type: application/json

{"requestId":"checkout-001","sceneCode":"checkoutOffer","input":{}}
```

| 字段 | 类型 | 必填与限制 | 语义 |
| --- | --- | --- | --- |
| `requestId` | string | 可省略或为空；当前没有额外长度校验 | 为空时由服务端生成 UUID；非空时沿用原值。 |
| `sceneCode` | string | 必填且不能空白；需指向已创建且绑定已发布版本的场景 | 选择本次执行的场景与入口版本。创建场景时的编码规则为字母开头、只含字母数字下划线连字符、最长 64 位。 |
| `input` | JSON object | DTO 未声明必填校验；调用方应传 `{}` 或节点需要的字段 | 提供本次执行的输入。具体字段由已发布流程的节点配置决定。 |

请求体最大 1 MiB。接口没有查询参数、分页参数或请求版本号；版本从场景当前发布指针取得。重复调用是两次独立执行；未提供 `requestId` 时每次生成新的标识，不承诺按相同 `requestId` 去重。

## 成功 HTTP 响应的数据结构

正常完成和已进入执行阶段的流程失败均沿用 HTTP 200，外壳 `code=SUCCESS`；须继续检查 `data.status`。

| `data` 字段 | 类型 | 含义 |
| --- | --- | --- |
| `requestId` | string | 本次请求标识。 |
| `sceneCode` | string | 本次执行的场景码。 |
| `versionId` | number | 本次固定选中的外层流程发布版本数据库 ID；不是版本号。 |
| `status` | `SUCCEEDED` 或 `FAILED` | 流程运行状态，不是业务结论。 |
| `businessResult` | object 或 `null` | 新发布流程的最终业务结果；旧发布版本成功时为 `null`，运行失败时也为 `null`。 |
| `nodeResults` | JSON object | 按节点 ID 保存的运行数据，不等于最终业务结果。 |
| `trace` | array | 节点执行轨迹；元素含 `requestId`、`sceneCode`、`flowVersion`（版本号）、`nodeId`、`startedAt`、`endedAt`、`status`、`errorCode`。时间为 ISO 8601 字符串。 |
| `errorCode` | string 或 `null` | 成功时为 `null`；链执行失败时为 `FLOW_EXECUTION_FAILED`，含结果节点但未产出结果时为 `BUSINESS_RESULT_MISSING`。 |

### 旧发布版本的成功响应示例

以下示例假设 `demoScene` 已绑定并发布含 `emit-value` 节点的流程。请求可用 `{ "requestId": "demo-001", "sceneCode": "demoScene", "input": {} }`；版本 ID 和时间以实际环境为准。

```json
{
  "code": "SUCCESS",
  "message": "成功",
  "data": {
    "requestId": "demo-001",
    "sceneCode": "demoScene",
    "versionId": 1,
    "status": "SUCCEEDED",
    "businessResult": null,
    "nodeResults": { "emit": "demo" },
    "trace": [{
      "requestId": "demo-001",
      "sceneCode": "demoScene",
      "flowVersion": 1,
      "nodeId": "emit",
      "startedAt": "2026-10-14T03:00:00Z",
      "endedAt": "2026-10-14T03:00:00Z",
      "status": "SUCCEEDED",
      "errorCode": null
    }],
    "errorCode": null
  }
}
```

### 当前执行失败的响应形态

下面是响应结构示意。失败时已执行节点的 `nodeResults`、`trace` 可能非空，但不会产生正常业务结论。

```json
{
  "code": "SUCCESS",
  "message": "成功",
  "data": {
    "requestId": "demo-002",
    "sceneCode": "demoScene",
    "versionId": 1,
    "status": "FAILED",
    "businessResult": null,
    "nodeResults": {},
    "trace": [],
    "errorCode": "FLOW_EXECUTION_FAILED"
  }
}
```

### 新发布流程的业务结果

新发布流程执行 `RESULT` 后，`data.businessResult` 可包含以下结构。结论代码来自该发布版本的节点配置：

```json
{
  "conclusion": "LOW_VALUE_OFFER",
  "reasonCode": "ELIGIBLE_LOW_GROUP",
  "reason": "符合活动条件，建议低额优惠",
  "details": { "offerCode": "LOW_5_OFF_100" },
  "suggestedAction": {
    "code": "APPLY_OFFER",
    "parameters": { "offerCode": "LOW_5_OFF_100" }
  }
}
```

`conclusion`、`reasonCode`、`reason` 为非空字符串；`details` 是业务明细对象，缺少明细时为 `{}`；`suggestedAction` 可为 `null`，存在时 `code` 非空且 `parameters` 是对象。营销示例代码不是平台固定枚举。建议动作不表示实际发券、用券或支付完成；这些事件由后续反馈能力记录。

只含一个结果节点的新发布流程可以返回以下完整响应；版本 ID 与时间依实际环境变化：

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
    "trace": [{
      "requestId": "checkout-001",
      "sceneCode": "checkoutOffer",
      "flowVersion": 1,
      "nodeId": "lowOfferResult",
      "startedAt": "2026-10-14T03:00:00Z",
      "endedAt": "2026-10-14T03:00:00Z",
      "status": "SUCCEEDED",
      "errorCode": null
    }],
    "errorCode": null
  }
}
```

## 错误与兼容

| 情况 | HTTP | 外壳 `code` | `data` |
| --- | --- | --- | --- |
| JSON 请求无法解析 | 400 | `INVALID_REQUEST` | `null` |
| `sceneCode` 空白等业务参数错误 | 422 | `INVALID_REQUEST` | `null` |
| 场景不存在或没有可执行发布版本 | 404 | `NOT_FOUND` | `null` |
| 请求体超过 1 MiB | 413 | `PAYLOAD_TOO_LARGE` | `null` |
| 未预期的服务端异常 | 500 | `INTERNAL_ERROR` | `null` |

这些错误由统一异常处理返回，没有 `data.status` 或 `data.businessResult`。执行过程中返回的 `status=FAILED` 则属于上面的 HTTP 200 响应形态。当前没有由此接口产生的版本并发冲突；执行时固定已选版本。

前端继续通过 `web/src/services/` 解包外壳；接入新字段时，旧发布版本的 `businessResult: null` 应显示为“该版本未定义业务结果”，不能显示成“无优惠”。可用于最小联调的顺序为：创建独立流程 → 保存含 `RESULT` 的草稿 → 校验并发布 → 创建场景并绑定该流程 → 调用本接口，核对结论和固定版本。流程创建与绑定契约见[流程资产接口](flow-assets.md)。

## 调用子流程后的节点结果

已发布外层流程包含 `SUBFLOW` 节点时，成功调用的结果放在 `data.nodeResults[调用节点ID]`，例如：

```json
{
  "flowCode": "offerEligibility",
  "versionId": 41,
  "status": "SUCCEEDED",
  "businessResult": {
    "conclusion": "ELIGIBLE",
    "reasonCode": "RULE_MATCHED",
    "reason": "满足资格规则",
    "details": {},
    "suggestedAction": { "code": "REVIEW_OFFER", "parameters": {} }
  },
  "trace": []
}
```

`versionId` 是本次真正执行的子流程不可变版本 ID。子流程结果归属调用节点，不直接改变外层 `data.businessResult`；外层最终结论仍由外层的 `RESULT` 节点配置决定。子流程业务上正常返回“不新增优惠”等结论时，外层可以继续执行。若输入映射缺值或子流程运行失败，外层返回 `data.status=FAILED`、`data.businessResult=null`、`data.errorCode=FLOW_EXECUTION_FAILED`；失败调用节点的 `nodeResults` 至少保留 `flowCode`、`versionId`、`status=FAILED` 和失败 `errorCode`，`trace` 中也标出调用节点。子流程已开始执行时，该调用结果另含 `childTrace`，可定位子流程内的失败节点；映射值缺失发生在子流程开始前，不会有 `childTrace`。当前没有独立的决策详情查询接口，结果与轨迹只随本次同步响应返回。
子流程没有建议动作时，调用节点下的 `businessResult` 省略 `suggestedAction`；外层响应的 `data.businessResult.suggestedAction` 按 T1 契约可为 `null`。
