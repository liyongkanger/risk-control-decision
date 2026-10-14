# 显式业务结果节点实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax. 用户已选择在当前工作区按单会话顺序执行。

**Goal:** 新发布的决策流程每条可执行路径恰好执行一个 `RESULT` 节点，并在成功响应中返回配置的业务结果。

**Architecture:** 后端新增独立结果节点及发布专用的路径覆盖校验；编译器把节点映射到 LiteFlow 适配器，执行上下文保存一次原始结果配置，决策服务转换为 T1 的 `BusinessResult`。旧发布版本不含结果节点，继续返回 `null`。示例图补上结果节点以便重新发布。

**Tech Stack:** Java 21、Spring Boot 4.1.1、LiteFlow 2.16.3、JUnit 5、H2、React/TypeScript 演示模板。

**Spec:** `docs/superpowers/specs/2026-10-14-explicit-result-node-design.md`

## Global Constraints

- 先读并遵循 `docs/standards/project-conventions.md`、`docs/standards/mvc-layer-conventions.md`、`docs/standards/frontend-backend-collaboration.md`；后端先行，接口文档在前端接入前与代码核对。
- 草稿仍允许没有业务结果；场景和独立流程的 `/validate` 与 `/publish` 都要求完整结果路径。旧不可变发布版本不回写、不重新校验。
- `RESULT` 不作为普通业务组件注册；原始配置留在 LiteFlow 组件包，`decision` 模块构造 `BusinessResult`。
- 现有 `master` 工作区有其他已暂存改动。执行中不得提交或清理这些改动；完成后仅报告本次文件及测试。
- 每次 Maven 命令先在 PowerShell 设置 `$env:JAVA_HOME = 'D:\enviorment\jdk21'`。测试按红灯、最小实现、绿灯执行。

---

## 文件职责

| 文件 | 职责 |
| --- | --- |
| `orchestrator/graph/NodeKind.java`、`GraphValidator.java` | 识别 `RESULT` 的节点类型及基本度数。 |
| `orchestrator/graph/ResultPathValidator.java` | 发布专用的结果配置、路径数量与并行区域校验。 |
| `component/liteflow/compile/FlowCompiler.java` | 将 `RESULT` 编译为专用 LiteFlow 节点。 |
| `component/liteflow/node/result/ResultNodeAdapter.java` | 从节点配置写入单次执行结果槽和轨迹。 |
| `component/liteflow/context/ExecutionContext.java`、`service/LiteFlowRunResult.java`、`service/LiteFlowExecutionService.java` | 传递可空的原始结果配置。 |
| `decision/service/impl/DecisionExecutionServiceImpl.java` | 成功执行后生成 T1 业务结果；失败不生成业务结论。 |
| `policy/service/impl/FlowPublicationServiceImpl.java`、`FlowAssetServiceImpl.java` | 场景与独立流程的验证、发布及试运行都接入结果校验和配置。 |
| `web/src/flow/types.ts`、`templates.ts`、`all-node-example.json` | 增加结果节点类型，并使显式示例模板可重新发布。 |
| `docs/api/decision-results.md`、`docs/api/flow-assets.md` | 更新真实可调用契约和图校验错误码。 |

### Task 1: 校验结果节点配置与每条执行路径

**Files:** 修改 `NodeKind.java`、`GraphValidator.java`；创建 `ResultPathValidator.java`、`src/test/java/com/dmp/riskdecision/orchestrator/graph/ResultPathValidatorTest.java`。

**Interfaces:** `ResultPathValidator#validate(GraphDefinition): GraphValidationResult`；先用现有 `GraphValidator#validateForPublish` 排除坏结构，再在两个发布用例中调用专用校验器。错误码为 `RESULT_MISSING`、`RESULT_DUPLICATE`、`RESULT_PARALLEL_UNSUPPORTED`、`RESULT_CONFIG_INVALID`。

- [x] 写失败测试：串行图一个结果通过；无结果、同一路径两个结果、条件分支缺一边、条件两边各一个结果、并行分支结果、结果后再次并行、配置空代码或未知字段分别给出预期问题码和 `nodeId`。使用字面量图输入与字面量错误码，不用被测算法生成预期值。
- [x] 运行 `mvn '-Dtest=ResultPathValidatorTest' test`，确认因新类型或校验器缺失而失败。
- [x] 新增 `NodeKind.RESULT` 与一入一出的度数规则。`ResultPathValidator` 先校验配置形态：必填字符串、可选对象、未知键、`componentKey` 与 `failurePolicy` 均为空；再对无环图做拓扑传播，每个节点保存从起点到该处可能的结果计数 `{0,1,2}`，其中 2 表示两个及以上。结果节点计数加一，`END` 只能收到 `{1}`。对所有 `RESULT` 检查并行区域限制，利用并行拆分到匹配汇合的可达区域，不枚举指数级路径；结构不完整时返回基础错误，避免空指针。
- [x] 运行 `mvn '-Dtest=ResultPathValidatorTest,GraphValidatorTest' test`，确认新路径案例和原有结构测试通过。

### Task 2: 编译与执行结果节点

**Files:** 修改 `FlowCompiler.java`、`ExecutionContext.java`、`LiteFlowRunResult.java`、`LiteFlowExecutionService.java`、`DecisionExecutionServiceImpl.java`；创建 `component/liteflow/node/result/ResultNodeAdapter.java`；修改 `FlowCompilerTest.java`、`DecisionExecutionServiceTest.java`，必要时新增聚焦集成测试。

**Interfaces:** EL 节点 ID 固定为 `businessResult`，每个 `RESULT` 使用自己的 `.tag("<nodeId>")`；`ExecutionContext#setBusinessResultConfig(Map)` 只能成功一次，`LiteFlowRunResult#getBusinessResultConfig()` 返回可空配置。保留 `LiteFlowRunResult` 原四参数构造方法以兼容现有调用和测试。

- [x] 写失败测试：编译包含 `RESULT` 的图时 EL 包含 `businessResult.tag(...)`；运行包含结果节点的链后返回结论和原因；同一请求第二次设置结果槽会失败；旧无结果版本仍成功且 `businessResult=null`。
- [x] 运行 `mvn '-Dtest=FlowCompilerTest,DecisionExecutionServiceTest' test`，确认新增断言因 RESULT 尚不能编译或运行而失败。
- [x] 让编译器把 `RESULT` 当作可执行节点计入 seen/bindings，跳过组件目录；专用适配器只从 `ExecutionContext.nodeConfigs` 读取配置并设置一次结果槽，按既有 `NodeTrace` 结构记成功或失败。`LiteFlowExecutionService` 将原始配置传给 `LiteFlowRunResult`。
- [x] `DecisionExecutionServiceImpl` 对成功且存在结果槽的请求构造 `BusinessResult`；若图含 `RESULT` 但槽为空，返回 `FAILED` 与 `BUSINESS_RESULT_MISSING`；链失败保持 `FLOW_EXECUTION_FAILED` 与空业务结果。无 `RESULT` 的旧图继续成功返回空结果。
- [x] 运行 `mvn '-Dtest=FlowCompilerTest,DecisionExecutionServiceTest' test`，确认新旧路径通过。

### Task 3: 场景与独立流程发布统一强制结果校验

**Files:** 修改 `FlowPublicationServiceImpl.java`、`FlowAssetServiceImpl.java`；修改 `FlowPublicationServiceTest.java`、`FlowApiTest.java`、`FlowAssetApiTest.java`、`SceneApiTest.java`、`SceneFlowOwnershipTest.java` 与依赖 `simpleGraph` 的发布测试。

**Interfaces:** 两类服务的 `validate` 在基础图与组件校验后调用 `ResultPathValidator`；`publish` 在创建版本前拒绝结果路径问题，仍返回可定位的 `GraphIssue`。发布试运行将 `RESULT` 的节点配置带入执行器。

- [x] 先写失败的 HTTP 或服务集成测试：场景和独立流程缺少结果时 `/validate` 返回 `RESULT_MISSING`，发布返回图校验失败且指针不切换；含结果的流程发布与执行后 `data.businessResult.conclusion` 等于配置值。
- [x] 运行聚焦的 `mvn '-Dtest=FlowPublicationServiceTest,FlowAssetApiTest,FlowApiTest' test`，确认缺少校验导致测试失败。
- [x] 在两个服务中以同一顺序接入结果校验；验证与发布共享规则。修改试运行配置收集条件，纳入 `RESULT`；保持发布版本写入和条件指针切换顺序不变。
- [x] 更新测试中实际要发布的简单图，在原 `emit` 与 `END` 间插入静态 `RESULT`，使旧测试继续验证原来的版本、并发和节点行为。纯图结构校验测试无需增加结果节点。运行 `mvn '-Dtest=FlowPublicationServiceTest,FlowAssetApiTest,FlowApiTest,SceneApiTest,SceneFlowOwnershipTest' test`。

### Task 4: 示例模板、接口文档与全量回归

**Files:** 修改 `web/src/flow/types.ts`、`templates.ts`、`templates.test.ts`、`all-node-example.json`、`docs/api/decision-results.md`、`docs/api/flow-assets.md`、`docs/references/all-node-test-flow.md`，以及受结果发布约束影响的剩余测试夹具。

**Interfaces:** `GraphDefinition.schemaVersion` 仍为 1；`RESULT` 的 JSON 配置按设计文档。演示模板提供可发布结果，空白模板继续只放 `START`/`END` 并保持草稿未发布语义。

- [x] 先更新前端模板测试：显式演示模板与全节点示例都含 `RESULT` 且各条件路径可得到业务结论；空白草稿仍无结果。运行 `npm test -- --run` 确认新断言先失败。
- [x] 更新模板及 TypeScript 节点类型；全节点示例的 `RESULT` 放在并行汇合与条件汇合后，保持两种条件输入都可运行；更新说明文档，避免仍称示例无业务结论。运行 `npm test -- --run`、`npm run typecheck`、`npm run build`。
- [x] 更新两份 `docs/api/` 契约，将后端实际可调用的非空业务结果、`RESULT` 配置及错误码写成正式范围；明确前端完整编辑控件仍待接入。
- [x] 使用 Java 21 运行 `mvn test`；只为实际失败的旧发布夹具补结果节点。运行限定本次路径的 `git diff --check` 并核对工作区与暂存区；已有其他暂存内容保留，本轮不自动提交。

## 完成条件

- 结果节点在串行与条件路径运行成功时写入业务结果；遗漏、重复、并行争写和配置错误均在发布前可定位。
- 旧已发布图按 T1 兼容语义继续执行；新发布图必须有明确结果。
- 场景与独立流程的验证和发布规则一致，前后端演示模板与接口文档一致。
