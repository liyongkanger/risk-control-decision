# 流程资产与场景绑定接口

日期：2026-10-13  
状态：后端已实现并通过 H2 集成测试；流程资产与子流程画布已按本接口接入，并在隔离 H2 环境完成联调。

本文供前端接入独立流程资产时使用。实现入口为 `FlowAssetController`、`SceneController`；当前所有接口均不要求登录，后续鉴权方案另定。

## 共同约定

- 基础路径为 `/api`。成功统一返回 HTTP 200，响应外壳为 `{ "code": "SUCCESS", "message": "成功", "data": ... }`。
- `flowCode`、`sceneCode` 以字母开头，只能包含字母、数字、下划线和连字符，最长 64 位。创建流程时 `flowName` 去除首尾空格后需为 1～128 字。
- `expectedRevision` 是草稿的预期修订号。首次保存传 `0`，之后传上次保存返回的 `revision`；修订号冲突返回 HTTP 409。
- 分页查询从 `page=1` 开始，`pageSize` 范围为 1～100；`keyword` 最长 128 字符，按流程码或名称搜索，`%`、`_` 等字符按普通字符处理。
- 时间字段 `updatetime` 使用接口实际返回的本地日期时间字符串，例如 `2026-10-13T16:00:00`。前端不以该字段做版本并发判断。

## 接口清单

| 方法与路径 | 用途 | 请求 | `data` |
| --- | --- | --- | --- |
| `POST /api/flows` | 创建独立流程 | `{ "flowCode": "riskCheck", "flowName": "交易风险决策" }` | `FlowInfoResp` |
| `GET /api/flows?keyword=&page=1&pageSize=20` | 分页搜索独立流程 | 查询参数；省略时分别为 `""`、`1`、`20` | `PageFlowResp` |
| `GET /api/flows/{flowCode}` | 获取流程详情 | 路径中的流程码 | `FlowInfoResp` |
| `GET /api/flows/{flowCode}/published` | 获取当前发布版本输入摘要 | 路径中的流程码 | `PublishedFlowSummaryResp` |
| `PUT /api/flows/{flowCode}/draft?expectedRevision=0` | 保存流程草稿 | `FlowGraphReq` | `FlowAssetDraftResp` |
| `GET /api/flows/{flowCode}/draft` | 获取流程草稿 | 路径中的流程码 | `FlowAssetDraftResp` |
| `POST /api/flows/{flowCode}/validate` | 校验待发布图 | `FlowGraphReq` | `FlowValidationResp` |
| `POST /api/flows/{flowCode}/publish` | 发布草稿 | `{ "expectedRevision": 1 }` | `FlowAssetVersionResp` |
| `PUT /api/scenes/{sceneCode}/entry-flow` | 将场景绑定到已有流程 | `{ "flowCode": "riskCheck" }` | `SceneResp` |

`FlowInfoResp` 字段：`id: number`、`flowCode: string`、`flowName: string`、`flowType: "DECISION"`、`publishedVersionId: number | null`、`publishedVersionNumber: number | null`、`updatetime: string`。当前只创建 `DECISION` 类型；未发布时两个版本字段都为 `null`。

`PageFlowResp` 字段：`records: FlowInfoResp[]`、`total: number`、`page: number`、`pageSize: number`。`total` 是相同搜索条件下的总条数；列表按更新时间降序、ID 降序稳定排序，超出末页时 `records` 为空数组。

`FlowAssetDraftResp` 字段：`flowCode: string`、`graph: FlowGraphReq`、`revision: number`。

`FlowAssetVersionResp` 字段：`id: number`、`flowCode: string`、`versionNumber: number`、`chainId: string`。`chainId` 是运行链标识，前端选择版本时应使用版本 ID 或版本号，不解析其字符串格式。

`PublishedFlowSummaryResp` 字段：`id: number`、`flowCode: string`、`versionNumber: number`、`requiredInputs: string[]`。这是当前发布版本的快照摘要，供外层画布选择 `SUBFLOW.config.versionId` 并逐项配置输入映射。未发布时返回 HTTP 404、`code: "NOT_FOUND"`、`data: null`；子流程再次发布后重新读取会得到新的 ID，旧草稿不会自动升级。

```http
GET /api/flows/offerEligibility/published
```

```json
{
  "code": "SUCCESS",
  "message": "成功",
  "data": { "id": 41, "flowCode": "offerEligibility", "versionNumber": 2,
    "requiredInputs": ["userId", "orderAmount"] }
}
```

`SceneResp` 在原有 `sceneCode`、`sceneName`、`description`、`publishedVersionNumber`、`updatetime` 之外增加 `flowCode`。`publishedVersionNumber` 在场景尚未选中已发布版本时为 `null`。

### 流程图请求示例

`PUT .../draft` 和 `POST .../validate` 直接提交图对象，不额外包一层 `graph`：

```json
{
  "schemaVersion": 1,
  "nodes": [
    { "id": "start", "kind": "START", "componentKey": null, "failurePolicy": null, "config": {} },
    { "id": "emit", "kind": "COMPONENT", "componentKey": "emit-value", "failurePolicy": "FAIL_FAST", "config": { "value": "demo" } },
    { "id": "end", "kind": "END", "componentKey": null, "failurePolicy": null, "config": {} }
  ],
  "edges": [
    { "id": "edge1", "source": "start", "target": "emit", "branch": null },
    { "id": "edge2", "source": "emit", "target": "end", "branch": null }
  ],
  "layout": {}
}
```

草稿允许暂时不完整，但发布前必须通过图结构与组件配置校验。当前图 `schemaVersion` 为 `1`。

### 成功响应示例

以下示例假设提交的是尚未编辑的空草稿；返回的 `graph` 与请求内容相同：

```json
{
  "code": "SUCCESS",
  "message": "成功",
  "data": { "flowCode": "riskCheck", "graph": { "schemaVersion": 1, "nodes": [], "edges": [], "layout": {} }, "revision": 1 }
}
```

发布成功返回的 `data` 示例：

```json
{ "id": 41, "flowCode": "riskCheck", "versionNumber": 1, "chainId": "flow_riskCheck_v1" }
```

分页查询返回的 `data` 示例：

```json
{
  "records": [
    { "id": 12, "flowCode": "riskCheck", "flowName": "交易风险决策", "flowType": "DECISION", "publishedVersionId": 41, "publishedVersionNumber": 1, "updatetime": "2026-10-13T16:00:00" }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 20
}
```

### 校验与失败响应

校验通过返回 HTTP 200，`data` 为 `{ "valid": true, "issues": [], "compiledEl": "..." }`。校验不通过返回 HTTP 422、`code: "GRAPH_INVALID"`；此时 `data` 仍是 `FlowValidationResp`，包含 `valid: false` 和 `issues`。每个问题包含 `code`、`nodeId`、`edgeId`、`message`，可用于定位画布节点或连线。

新发布或重新发布的决策流程必须在每条可执行结束路径上恰好执行一个 `RESULT` 节点。草稿可暂时没有结果节点；保存草稿成功不表示可以发布。`RESULT` 节点一入一出，`componentKey`、`failurePolicy` 为 `null`，配置示例：

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

`conclusion`、`reasonCode`、`reason` 必须是非空字符串；`details` 与 `suggestedAction.parameters` 省略时按空对象，`suggestedAction` 可省略。条件分支可各放一个结果节点；并行分支中不可放结果节点，须在汇合后确定结论。校验问题码包括 `RESULT_MISSING`、`RESULT_DUPLICATE`、`RESULT_PARALLEL_UNSUPPORTED`、`RESULT_CONFIG_INVALID`，`nodeId` 指向相关节点或 `END`。历史已发布版本不回写，仍可按原行为执行；其业务结果为空，见[决策结果接口](decision-results.md)。

| HTTP | `code` | 典型场景 | `data` |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | JSON 格式无法解析 | `null` |
| 404 | `NOT_FOUND` | 流程、场景或草稿不存在 | `null` |
| 409 | `FLOW_CODE_CONFLICT` | 创建流程时流程码已被使用 | `null` |
| 409 | `VERSION_CONFLICT` | 草稿修订号或发布指针冲突 | `null` |
| 422 | `INVALID_REQUEST` | 流程码、名称或分页参数等无效 | `null` |
| 422 | `GRAPH_INVALID` | 图校验失败 | 校验接口返回 `FlowValidationResp`；保存或发布接口返回问题列表 |
| 413 | `PAYLOAD_TOO_LARGE` | 请求体超出 1 MiB | `null` |

未知服务端异常返回 HTTP 500、`code: "INTERNAL_ERROR"`，不包含内部堆栈。

## 版本与场景关系

1. 流程资产可在没有场景的情况下创建草稿和发布版本；发布后 `FlowInfoResp.publishedVersionId` 指向该流程当前发布版本。
2. 新建场景时后端为其创建并绑定同名入口流程，以保持现有场景页面可用。
3. 调用 `entry-flow` 时，场景绑定给定流程，并选中该流程**当时**的发布版本；若流程尚未发布，场景当前发布版本为空。
4. 通过独立流程接口发布新版本，不会自动推进已绑定场景的发布指针。再次调用 `entry-flow` 绑定同一流程，可以让场景选中流程当前版本；通过原场景发布接口发布时，也会更新该场景的发布指针。
5. 原有 `/api/scenes/{sceneCode}/draft`、`/validate`、`/publish` 仍可用，操作的是场景当前绑定的流程；其响应继续使用 `sceneCode`，避免现有前端接口破坏。

最小联调顺序：创建流程 → 保存草稿 → 校验 → 发布 → 创建场景 → 绑定入口流程 → 调用 `POST /api/decisions/evaluate` 检查场景选中的版本。

## 决策子流程引用（前后端已接入）

流程图可在 `nodes` 中使用 `SUBFLOW` 调用节点。子流程必须先独立发布，外层草稿选择其当前发布版本 ID；发布外层前仍要求该 ID 是子流程当前发布版本。发布成功后，外层不可变图固定此 ID，子流程以后发布新版本不会改变外层执行结果。当前只支持外层调用一层子流程，子流程本身不能再含 `SUBFLOW`。

```json
{
  "id": "eligibilityCall",
  "kind": "SUBFLOW",
  "componentKey": null,
  "failurePolicy": "FAIL_FAST",
  "config": {
    "flowCode": "offerEligibility",
    "versionId": 41,
    "inputMappings": {
      "userId": { "source": "REQUEST_INPUT", "key": "userId" },
      "previous": { "source": "NODE_RESULT", "nodeId": "priorCall", "path": "businessResult.conclusion" }
    }
  }
}
```

被引用流程的图顶层可声明 `requiredInputs: string[]`，例如 `requiredInputs: ["userId", "previous"]`。调用节点的 `inputMappings` 必须覆盖且仅覆盖这些字段；未声明时为空数组。`REQUEST_INPUT` 只取本次外层请求中的指定字段，`NODE_RESULT` 只取已在每条到达调用节点的路径上执行过的节点结果字段。点路径逐层读取 JSON 对象，不支持表达式或数组下标。运行时映射值缺失会使该调用失败，不会把外层全部输入隐式传给子流程。图的 `schemaVersion` 仍为 1；旧图缺少 `requiredInputs` 时按空数组处理。

`/validate` 返回可定位的 `SUBFLOW_CONFIG_INVALID`、`SUBFLOW_VERSION_UNAVAILABLE`、`SUBFLOW_INPUT_MAPPING_INVALID`、`SUBFLOW_NESTING_UNSUPPORTED` 或 `SUBFLOW_CYCLE`；`/publish` 遇到同类图问题返回 HTTP 422、`GRAPH_INVALID`，且不切换发布指针。发布前只接受子流程**当前**发布版本；切换外层指针时会在数据库事务中锁定并重查相关子流程行，若此时版本已变化则返回 HTTP 409、`VERSION_CONFLICT`。历史版本不能通过草稿直接新选，因为现有版本表尚无独立的“曾成功发布”状态记录。已经发布的外层流程可继续运行其固定的历史子流程版本。

条件节点可用已注册的 `equals-value` 组件读取子流程调用结果，例如 `{"sourceNodeId":"eligibilityCall","path":"businessResult.conclusion","expected":"ELIGIBLE"}`；该来源节点必须在条件节点的每条可执行路径上已运行。原 `is-positive` 组件及 `inputKey` 配置保持原有行为。外层仍须由自己的 `RESULT` 节点产生最终结论，子流程结论不会自动覆盖外层结果。

最小调用顺序：创建并发布子流程 → 从 `GET /api/flows/{flowCode}/published` 读取 ID 和必需输入 → 创建外层流程并保存带该版本 ID 的 `SUBFLOW` 图 → 校验、发布外层 → 创建场景并绑定外层流程 → 调用决策接口。前端接入时按[前后端协作规范](../standards/frontend-backend-collaboration.md)核对字段与行为。

## 当前页面与联调范围

后台 `/flows` 提供独立流程分页搜索、新建和画布入口；`/scenes` 显示入口流程与场景当前版本，并可绑定已有流程。前端列表使用本接口的 `total`、`page`、`pageSize`，遵循[后台列表分页约定](../standards/admin-list-pagination.md)。当前页面未提供历史版本列表或任意版本切换；重复绑定同一流程会选中它的当前发布版本。
