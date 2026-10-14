# 决策子流程引用与执行实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax. 用户已选择单会话执行，当前工作区在 master 且已有其他暂存内容，不自动提交。

**Goal:** 外层决策流程可引用一层已发布子流程，发布时固定版本，运行时隔离输入和输出，并由外层显式产出最终结论。

**Architecture:** `SUBFLOW` 节点保存 `flowCode`、当前发布 `versionId` 和输入映射；发布服务共用引用校验；LiteFlow 专用节点经端口调用决策服务，子流程在独立上下文中执行，结果按调用节点 ID 归属外层。外层结果仍由自己的 `RESULT` 节点产生。

**Tech Stack:** Java 21、Spring Boot 4.1.1、LiteFlow 2.16.3、MyBatis-Plus、H2、JUnit 5。

**Spec:** `docs/superpowers/specs/2026-10-14-decision-subflow-composition-design.md`

## Global Constraints

- 遵守 `docs/standards/project-conventions.md`、`mvc-layer-conventions.md`、`frontend-backend-collaboration.md`。
- 现有 `master` 暂存内容保留；只修改本切片必要的文件，不创建混合提交。
- 发布前子流程引用版本须等于其当前 `publishedVersionId`；发布后读取外层图中固定的版本 ID，绝不运行时跟随最新指针。
- 子流程不能再含 `SUBFLOW`；运行失败使外层失败且没有正常业务结果。
- 所有 Maven 命令在 PowerShell 中先设置 `$env:JAVA_HOME='D:\enviorment\jdk21'`。

---

### Task 1: 节点类型与引用校验

**Files:** `NodeKind.java`、`GraphValidator.java`、`FlowCompiler.java`、`FlowComponentValidationService.java`；新增 `SubflowReferenceValidator.java` 与 `SubflowReferenceValidatorTest.java`。

**Interfaces:** `SUBFLOW` 一入一出、`FAIL_FAST`、无组件 key；配置 `flowCode:string`、`versionId:long`、`inputMappings: object`。校验器返回 `GraphValidationResult`，错误定位到调用节点。

- [x] 写测试：当前发布版本通过；未发布、版本不匹配、自引用、被引用图含 `SUBFLOW`、坏映射、缺映射均失败；同一子流程两个调用节点可通过。
- [x] 运行聚焦测试，确认缺少新能力导致红灯。
- [x] 实现结构、配置与引用校验。编译器将 `SUBFLOW` 映射到 `decisionSubflow.tag("nodeId")`，不从普通组件目录解析。
- [x] 运行聚焦测试及原图/编译测试。

### Task 2: 独立执行、结果归属与失败传播

**Files:** 新增 `component/liteflow/service/SubflowInvoker.java`、`component/liteflow/node/subflow/SubflowNodeAdapter.java`、`decision/service/impl/SubflowInvokerImpl.java`；修改 `ExecutionContext.java`、`LiteFlowExecutionService.java`、`DecisionExecutionServiceImpl.java`、`LiteFlowConditionAdapter.java` 及对应测试。

**Interfaces:** 子流程调用使用固定 `FlowVersion` 与新执行上下文；外层 `nodeResults[nodeId]` 为包含 `flowCode`、`versionId`、`status`、`businessResult` 的对象；外层 `businessResult` 不被子流程写入。输入映射只读取请求字段或已执行节点的字段；条件组件可按受控来源判断子流程结论。

- [x] 写集成测试：两个调用节点结果隔离、同一子流程不同输入、成功业务结论与运行失败区别、外层最终结论由外层 `RESULT` 决定。
- [x] 运行测试确认红灯。
- [x] 实现专用节点、受控输入映射、子流程独立上下文与失败轨迹；新增条件组件读取节点结果的显式配置。
- [x] 运行聚焦测试，确认结果与失败语义。

### Task 3: 发布固定、启动恢复与接口文档

**Files:** `FlowAssetServiceImpl.java`、`FlowPublicationServiceImpl.java`、`FlowAssetRepository.java`、`SceneFlowRepository.java`、`FlowMapper.java`、`FlowMapper.xml`；相关 API 测试及 `docs/api/flow-assets.md`、`docs/api/decision-results.md`。

**Interfaces:** 场景和独立流程共用引用校验；外层不可变图保存具体 `versionId`；启动或按需加载固定子流程链。外层发布前若引用版本已不是当前发布版本，返回可定位问题并不切换指针。

- [x] 写 API 集成测试：先发布子流程 v1、发布外层、再发布子流程 v2；外层仍调用 v1，重启式清理内存链后也一样；更新外层草稿到 v2 后才使用 v2。
- [x] 写失败测试：未发布、陈旧版本、嵌套、映射缺失不切换外层指针。
- [x] 运行聚焦测试确认红灯。
- [x] 两条发布路径接入同一校验和数据库最终版本锁定；含子流程图只验证链加载，运行能力由端到端测试证明；补按版本加载链与文档。
- [x] 运行完整 `mvn test`、前端 `npm test`/`typecheck`/`build`、限定本切片的 `git diff --check`，复核暂存区并报告边界。

## 自检

- 新发布外层版本可实际执行，不能只存引用元数据。
- 旧已发布无子流程图继续可运行。
- 子流程更新不影响外层固定版本，重复调用不覆盖结果，失败不生成正常结论。
