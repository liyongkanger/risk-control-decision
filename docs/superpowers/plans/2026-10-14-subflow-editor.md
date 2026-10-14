# 子流程画布接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. 用户已选择单会话执行；当前 master 有其他已暂存改动，不自动提交。

**Goal:** 让 X6 画布可配置子流程调用、结果节点并查看真实决策执行结果。

**Architecture:** 后端增加当前发布版本摘要只读接口；前端服务层统一解包；编辑器把已选版本及输入映射写入图数据，所有发布动作仍由现有保存与校验门控；试运行单独呈现执行与业务结果。

**Tech Stack:** Spring Boot 4.1.1、JUnit 5、React 19、TypeScript、Umi Max、AntV X6、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-14-subflow-editor-design.md`

## Global Constraints

- 先核对 `docs/standards/project-conventions.md`、`mvc-layer-conventions.md`、`frontend-backend-collaboration.md` 和 `docs/api/`，后端接口及文档先行。
- 保留旧流程草稿与发布版本语义；修改配置后要求重新保存、校验、发布。
- 当前工作区其他暂存内容不清理、不混合提交。

---

### Task 1: 当前发布版本摘要接口

**Files:** `policy/service/FlowAssetService.java`、`FlowAssetServiceImpl.java`、`FlowAssetController.java`、`FlowAssetAssembler.java`、`policy/vo/resp/PublishedFlowSummaryResp.java`、`FlowAssetApiTest.java`、`docs/api/flow-assets.md`。

- [x] 写 HTTP 测试：已发布流程返回版本 ID、号和 `requiredInputs`；未发布返回 404。
- [x] 运行聚焦测试确认红灯，实施只读接口与响应转换。
- [x] 运行聚焦测试，核对并更新接口文档。

### Task 2: 前端服务与可测配置模型

**Files:** `web/src/services/flow.ts`、`flow.test.ts`、`web/src/flow/subflowConfig.ts`、`subflowConfig.test.ts`、`web/src/flow/types.ts`、`web/src/flow/api.ts`。

- [x] 测试已发布版本读取、版本陈旧提示判断、输入映射构造与业务结果展示模型；先运行红灯。
- [x] 实现最小服务、类型与纯配置辅助函数；运行绿灯及类型检查。

### Task 3: 画布配置与试运行展示

**Files:** `web/src/flow/FlowEditor.tsx`、`editor.css`、相关前端测试与 `README.md`。

- [x] 增加节点入口、流程输入声明、子流程搜索与映射表单、`RESULT`/条件配置表单；任何改动清除校验快照。
- [x] 增加 JSON 试运行输入和结构化结果视图；区分失败、旧版空结果、外层结论与子流程结论。
- [x] 运行前端测试、类型检查和构建；在真实浏览器走一条配置/发布/执行链路，并核对异常提示。

### Task 4: 回归与交接

- [x] 运行完整后端与前端测试、构建，核对接口文档、`git diff --check` 和暂存区。
- [x] 记录当前页面可用范围、验证环境和仍缺少的模拟结算反馈能力；保留改动待用户决定提交。
