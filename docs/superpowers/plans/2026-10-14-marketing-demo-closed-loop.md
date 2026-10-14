# 结算优惠营销演示闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. 用户已选择当前工作区单会话实施；保留既有暂存内容，不自动提交。

**Goal:** 用真实决策流程和可重复模拟订单反馈跑通结算页优惠增量利润演示闭环。

**Architecture:** `business.marketing` 定义并初始化三条演示流程与场景，调用现有决策服务；内存仓储保存模拟结算与幂等反馈；纯指标计算器输出三组人均利润；后台独立页面展示并触发操作。

**Tech Stack:** Java 21、Spring Boot 4.1.1、LiteFlow、JUnit 5、React 19、Ant Design、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-14-marketing-demo-closed-loop-design.md`

## Global Constraints

- 遵守 `docs/standards/project-conventions.md`、`mvc-layer-conventions.md`、`frontend-backend-collaboration.md`；模拟金额用 `BigDecimal`。
- 后端 API 与 `docs/api/marketing-demo.md` 先行，前端按文档接入；不改已有场景与决策接口行为。
- 当前 master 有其他已暂存改动；不清理或混合提交。

---

### Task 1: 演示流程定义与初始化

**Files:** 新增 `business/marketing/MarketingDemoFlows.java`、`MarketingDemoService.java` 及集成测试；沿用 `FlowAssetService`、`SceneService`、`DecisionExecutionService`。

- [x] 写失败测试：初始化得到两条已发布子流程、一条已发布外层流程和场景；重复初始化版本不增加；资格、选择与外层结论通过真实执行核对。
- [x] 运行聚焦测试确认红灯；实现固定图构建与幂等初始化；运行绿灯。

### Task 2: 模拟结算、反馈去重与利润

**Files:** 新增 `business/marketing/CheckoutAttempt.java`、`MarketingDemoStore.java`、`MarketingDemoMetrics.java`、请求响应类及测试。

- [x] 写失败测试：稳定分组、互斥实际优惠、未用推荐券、未支付、重复反馈、退款修正与 30 人固定指标。
- [x] 运行聚焦测试确认红灯；实现内存仓储、事件状态转换和 `BigDecimal` 汇总；运行绿灯。

### Task 3: HTTP 契约

**Files:** 新增 `business/marketing/MarketingDemoController.java`、对应 Req/Resp、HTTP 集成测试与 `docs/api/marketing-demo.md`。

- [x] 写失败的成功/错误 HTTP 测试，覆盖初始化、固定样本、报告、反馈重放与冲突。
- [x] 运行红灯；实现薄 Controller 与响应转换；更新接口文档并运行绿灯。

### Task 4: 后台样板页面

**Files:** 新增 `web/src/services/marketingDemo.ts`、行为测试、`web/src/pages/marketing-demo/index.tsx`，修改 `web/config/routes.ts` 与必要样式。

- [x] 写服务层行为测试；实现统一解包与错误展示。
- [x] 实现初始化、固定样本、指标/明细、手动结算及反馈操作；明确标注模拟数据。
- [x] 运行前端测试、类型检查、构建，并用真实浏览器验证成功和退款路径。

### Task 5: 全量回归与交接

- [x] 运行完整后端/前端测试与构建，核对接口文档、固定指标及本轮文件差异。
- [x] 在 `docs/references/` 记录本地联调范围与内存数据重启边界，保留已有暂存内容。
