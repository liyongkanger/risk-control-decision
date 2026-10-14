# 决策业务结果契约实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. 本计划在当前会话按本地代码顺序执行，不要求分派子 Agent。

**Goal:** 在现有同步决策响应中增加明确、可空的 `businessResult`，使旧流程保持可运行，并为后续显式结果节点提供类型与接口契约。

**Architecture:** `decision.model` 持有业务结果及建议动作的受控对象，`ExecutionResult` 增加可空结果字段；`decision.vo.resp` 持有 HTTP 结构，`DecisionAssembler` 显式转换。执行服务在 T1 仍返回空业务结果；T2 才负责从流程中产出结果和发布校验。

**Tech Stack:** Java 21、Spring Boot 4.1.1、JUnit 5、AssertJ、Jackson、现有 `ApiResponse<T>`。

**Spec:** `docs/superpowers/specs/2026-10-14-decision-result-contract-design.md`

## Global Constraints

- 遵循 `docs/standards/project-conventions.md`、`docs/standards/mvc-layer-conventions.md` 与 `docs/standards/frontend-backend-collaboration.md`；新增 Java 类型写中文 Javadoc，响应数据类使用项目约定的 Lombok 写法。
- `POST /api/decisions/evaluate` 的路径、请求体、旧响应字段和 HTTP 行为保持现状。旧发布版本成功执行时 `businessResult` 必须序列化为 JSON `null`。
- `status=FAILED` 时 `businessResult=null`；正常“不新增优惠”须用非空业务结果表达。T1 不引入结果节点、发布校验、数据库迁移或前端展示变更。
- 当前工作区有其他已暂存改动。执行时先核对状态；不得把它们混入 T1 提交。若无法隔离暂存区，保留 T1 改动待用户统一提交。
- 使用 `JAVA_HOME=D:\enviorment\jdk21` 运行 Maven；默认 `JAVA_HOME` 指向 Java 8，测试 fork 会失败。

---

## 文件职责

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/dmp/riskdecision/decision/model/BusinessResult.java` | 业务结论、原因、明细与可选建议动作的领域结果。 |
| `src/main/java/com/dmp/riskdecision/decision/model/SuggestedAction.java` | 建议动作代码及参数；没有实际执行状态。 |
| `src/main/java/com/dmp/riskdecision/decision/model/ExecutionResult.java` | 在已有执行结果上增加可空的业务结果，同时保留旧构造方法。 |
| `src/main/java/com/dmp/riskdecision/decision/vo/resp/BusinessResultResp.java` | 对外嵌套业务结果结构。 |
| `src/main/java/com/dmp/riskdecision/decision/vo/resp/SuggestedActionResp.java` | 对外建议动作结构。 |
| `src/main/java/com/dmp/riskdecision/decision/vo/resp/EvaluateDecisionResp.java` | 增加 `businessResult`，保留现有字段。 |
| `src/main/java/com/dmp/riskdecision/decision/assembler/DecisionAssembler.java` | 显式转换领域结果与 HTTP 响应。 |
| `src/test/java/com/dmp/riskdecision/decision/DecisionResultContractTest.java` | 验证结果对象与响应转换。 |
| `src/test/java/com/dmp/riskdecision/policy/FlowAssetApiTest.java` | 验证真实旧流程 HTTP 响应仍含旧字段且新字段为 `null`。 |
| `docs/api/decision-results.md` | 草案阶段的同步决策接口契约；代码测试通过后标记已实现范围。 |

### Task 1: 建立受控业务结果对象与旧执行结果兼容入口

**Files:** 创建 `decision/model/BusinessResult.java`、`decision/model/SuggestedAction.java`、`decision/DecisionResultContractTest.java`；修改 `decision/model/ExecutionResult.java`。

**Interfaces:**
- `new SuggestedAction(String code, Map<String, Object> parameters)`；`getCode()`、`getParameters()`。
- `new BusinessResult(String conclusion, String reasonCode, String reason, Map<String, Object> details, SuggestedAction suggestedAction)`；对应 getter。
- `ExecutionResult` 保留现有七参数构造方法，并新增末尾带 `BusinessResult businessResult` 的八参数构造方法及 `getBusinessResult()`。

- [x] **Step 1: 先写失败的领域契约测试。** 在 `DecisionResultContractTest` 验证完整营销结果、没有建议动作的“不新增优惠”、空代码与空原因拒绝、失败执行不能携带业务结果，以及旧七参数 `ExecutionResult` 的结果为 `null`。关键断言如下：

```java
BusinessResult offer = new BusinessResult("LOW_VALUE_OFFER", "ELIGIBLE_LOW_GROUP",
        "符合活动条件", Map.of("offerCode", "LOW_5_OFF_100"),
        new SuggestedAction("APPLY_OFFER", Map.of("offerCode", "LOW_5_OFF_100")));
assertThat(offer.getConclusion()).isEqualTo("LOW_VALUE_OFFER");
assertThat(offer.getSuggestedAction().getParameters()).containsEntry("offerCode", "LOW_5_OFF_100");
assertThatThrownBy(() -> new BusinessResult(" ", "REASON", "说明", Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> new BusinessResult("NO_NEW_OFFER", "REASON", " ", Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class);
```

- [x] **Step 2: 运行红灯。** 执行 `mvn -Dtest=DecisionResultContractTest test`（本命令及后续 Maven 命令先在同一 PowerShell 进程设置 `$env:JAVA_HOME = 'D:\enviorment\jdk21'`）；预期因新类或新构造方法不存在而失败。
- [x] **Step 3: 实现最小领域模型。** 两个结果类使用 `final` 字段、显式构造方法和 getter。`conclusion`、`reasonCode`、`reason`、建议动作 `code` 拒绝 `null` 或空白；`details`、`parameters` 为 `null` 时转为空映射，其他情况复制后暴露只读视图。`suggestedAction` 允许 `null`。`ExecutionResult` 旧构造方法委托新构造方法并传 `null`，新字段参与 `equals` 与 `hashCode`，新构造方法拒绝 `status=FAILED` 且结果非空；执行服务现有调用保持不变。

```java
public ExecutionResult(String requestId, String sceneCode, long versionId, String status,
                       Map<String, Object> nodeResults, List<NodeTrace> trace, String errorCode) {
    this(requestId, sceneCode, versionId, status, nodeResults, trace, errorCode, null);
}
```

- [x] **Step 4: 运行绿灯与现有执行测试。** 执行 `mvn -Dtest=DecisionResultContractTest,DecisionExecutionServiceTest test`；确认新测试通过，旧执行结果及版本固定测试仍通过。

### Task 2: 扩展 HTTP 响应并核对旧流程真实调用

**Files:** 创建 `decision/vo/resp/BusinessResultResp.java`、`decision/vo/resp/SuggestedActionResp.java`；修改 `decision/vo/resp/EvaluateDecisionResp.java`、`decision/assembler/DecisionAssembler.java`、`decision/DecisionResultContractTest.java`、`policy/FlowAssetApiTest.java`。

**Interfaces:** `EvaluateDecisionResp.businessResult: BusinessResultResp | null`；响应对象字段和 JSON 名称按设计文档逐一对应。`DecisionAssembler#toEvaluateResp(ExecutionResult)` 是唯一转换入口，保留旧字段的现有映射。

- [x] **Step 1: 补写失败的映射与 HTTP 兼容测试。** 在 `DecisionResultContractTest` 构造带业务结果的 `ExecutionResult`，调用 `new DecisionAssembler().toEvaluateResp(result)`，断言 `conclusion`、`reasonCode`、`reason`、`details.offerCode`、`suggestedAction.code` 和 `parameters.offerCode`；再断言旧七参数结果转换后 `getBusinessResult()` 为 `null`。在 `FlowAssetApiTest#publishesFlowWithoutSceneAndBindsPinnedVersionToScene` 的首次执行响应后加入：

```java
assertThat(firstDecision.get("status").asText()).isEqualTo("SUCCEEDED");
assertThat(firstDecision.get("businessResult").isNull()).isTrue();
assertThat(firstDecision.get("nodeResults").get("emit").asInt()).isEqualTo(1);
```

- [x] **Step 2: 运行红灯。** 执行 `mvn -Dtest=DecisionResultContractTest,FlowAssetApiTest test`；预期新字段 getter 或 JSON 字段断言失败。
- [x] **Step 3: 实现响应映射。** `BusinessResultResp` 与 `SuggestedActionResp` 用 `@Data` 和普通类字段；`EvaluateDecisionResp` 按现有风格增加 `BusinessResultResp businessResult` 及 getter/setter。`DecisionAssembler` 在结果非空时转换所有字段，建议动作为空时输出 `null`；为空时显式设置 `null`。不修改 `ApiResponse`、Controller 路由及执行服务。

```java
BusinessResult businessResult = result.getBusinessResult();
response.setBusinessResult(businessResult == null ? null : toBusinessResultResp(businessResult));
```

- [x] **Step 4: 运行绿灯。** 执行 `mvn -Dtest=DecisionResultContractTest,FlowAssetApiTest test`；确认真实 HTTP JSON 含 `businessResult: null`，旧字段值不变。

### Task 3: 交付接口文档并完成后端回归

**Files:** 创建 `docs/api/decision-results.md`；核对 `docs/superpowers/specs/2026-10-14-decision-result-contract-design.md` 与实际代码，只有发现偏差才修改设计文档。

**Interfaces:** `POST /api/decisions/evaluate` 请求及 `ApiResponse<EvaluateDecisionResp>` 响应；本文记录已实现的空结果兼容行为，并把非空结果产出、路径校验、子流程结果列为待后续任务实现。

- [x] **Step 1: 写接口文档。** 按前后端交接规范记录请求字段、响应字段与空值、HTTP/`code`/`status` 的区别、旧版本成功与执行失败的 JSON 示例、固定版本语义、当前可调用范围和 T2 后续范围。非空营销结果示例标注为“拟议，待 T2 实现”，不得写成当前真实接口。
- [x] **Step 2: 全量后端验证。** 设置 Java 21 后执行 `mvn test`；检查 Surefire 汇总无失败。执行 `git diff --check`，核对 `git diff` 与 `git diff --cached`，确认没有把其他已暂存内容纳入本次工作结论。
- [x] **Step 3: 提交前隔离。** 只有暂存区能明确只包含 T1 的代码、测试和接口文档时，才按 `docs/standards/commit-conventions.md` 创建 `feat(decision): 增加可空的业务结果响应契约` 提交；若其他暂存内容仍在，保留 T1 改动并报告，不改动或提交用户已有暂存内容。

## 自检

- T1 只提供业务结果类型与响应字段；实际非空结果产出由 T2 实现，子流程结果由 T3/T4 实现。
- 旧版成功、正常“不新增优惠”和执行失败三种语义在设计文档、测试与接口文档中保持一致。
- 前端接入前，后端接口文档和真实 HTTP 响应必须重新核对。
