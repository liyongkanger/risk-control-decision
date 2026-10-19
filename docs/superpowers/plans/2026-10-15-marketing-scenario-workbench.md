# 营销场景一站式工作台实施计划

状态：第一版已实施；原复选框保留实施前的步骤，实际验证与调整见末尾执行记录。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有营销演示页串起版本准备、试决策、实际反馈、增量复盘与下一版引导，并让场景版本切换成为显式操作。

**Architecture:** `business.marketing` 通过 `policy.service` 只读查询场景所选流程版本及各固定引用；既有营销接口继续负责模拟结算和反馈。报告在后端用 `BigDecimal` 计算增量与比率；前端工作台使用现有规则中心、流程画布的深链接，不复制编辑器。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus、LiteFlow、React、Ant Design、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-15-marketing-scenario-workbench-design.md`

## 全局约束

- 遵循 `docs/standards/project-conventions.md`、`mvc-layer-conventions.md` 和 `frontend-backend-collaboration.md`；后端接口与 `docs/api/marketing-demo.md` 先行，再接页面。
- 保留本仓库现有暂存改动，不重置、不顺手提交；本轮不修改历史 Flyway 迁移。
- 状态读取是只读操作；规则、资格流程和外层流程仍分别发布；场景选用外层版本须显式触发。
- 订单、反馈和固定样本继续仅保存在当前进程；报告不声称真实营销效果。

## 文件职责

| 文件 | 职责 |
| --- | --- |
| `policy/service/{SceneService,FlowAssetService}.java` 及实现 | 向业务层提供选中场景版本和固定流程版本读取 |
| `business/marketing/service/MarketingDemoStatusService.java` 及实现、`model/MarketingDemoStatus.java` | 汇总当前运行链与当前资产版本，不修改指针 |
| `business/marketing/controller/MarketingDemoController.java`、`vo/resp/MarketingDemoStatusResp.java` | 暴露只读 `GET /status` |
| `business/marketing/service/impl/MarketingDemoServiceImpl.java`、`MarketingDemoFlowService.java` | 首次初始化回退、显式绑定、逐笔规则命中快照 |
| `business/marketing/MarketingDemoMetrics.java`、`model/GroupMetrics.java` | 增量人均利润、退款率、推荐使用率 |
| `web/src/pages/marketing-demo/` | 工作台分区、逐笔详情和反馈状态 |
| `web/src/services/marketingDemo.ts`、`docs/api/marketing-demo.md` | 前端接口类型和正式契约 |

## Task 1：只读策略状态及真实运行链

**接口：** `SceneService#getSelectedFlowVersion(sceneCode): FlowVersion`、`FlowAssetService#getVersion(id): FlowVersion`；`MarketingDemoStatusService#get(): MarketingDemoStatus`；`GET /api/demo/checkout-offer/status` 返回全部可空版本字段和 `initialized`。

- [ ] 先写 H2 API 测试：未初始化时 `GET /status` 返回 HTTP 200、`initialized=false` 且不创建资产；初始化后返回规则当前版、资格/选择/外层当前版及场景选中运行链；规则发布新版后运行链仍指向旧版。
- [ ] 跑 `mvn -Dtest=MarketingDemoStatusApiTest test`，核对失败点是缺少状态接口。
- [ ] 在 `policy.service` 添加必要的只读方法，业务 Service 仅通过这些方法查版本；状态服务按场景选中外层版本逐层解析 `SUBFLOW` 与 `execute-rule-set` 的固定版本 ID，部分资产缺失时返回可空字段。
- [ ] 写 `docs/api/marketing-demo.md` 的状态接口字段、空值、调用示例与失败语义；跑定向测试。

示例响应结构：

```json
{"initialized":true,"ruleCurrentVersionId":4,"eligibilityCurrentVersionId":8,
 "eligibilityPinnedRuleVersionId":4,"selectionCurrentVersionId":9,
 "outerCurrentVersionId":10,"outerCurrentVersionNumber":3,
 "outerPinnedEligibilityVersionId":8,"outerPinnedSelectionVersionId":9,
 "sceneSelectedOuterVersionId":10,"sceneSelectedOuterVersionNumber":3,
 "runningEligibilityVersionId":8,"runningSelectionVersionId":9,"runningRuleVersionId":4}
```

## Task 2：显式选用外层版本与逐笔规则追溯

**接口：** `POST /setup` 继续显式初始化或选用外层当前版本；`POST /checkout`、`POST /run-fixture` 只在样板尚未初始化时自动调用 `setup()`。`DemoCheckoutResp` 增加可空 `ruleSetCode`、`ruleVersionId`、`matchedRuleId`、`ruleReasonCode`。

- [ ] 写集成测试：先发布外层新版但不选用，两次结算和固定样本仍执行场景旧版；调用 `setup()` 后新结算执行新版。重复结算输入与反馈幂等语义不变。
- [ ] 写逐笔测试：不合格订单返回命中规则版本、行和原因；历史或失败记录的字段可为空，不将执行失败当成正常“不新增优惠”。
- [ ] 跑定向测试确认失败；提取“仅首次初始化”判断，避免每次结算隐式重绑；从资格子流程业务结果明细写入 `CheckoutAttempt`，通过响应类暴露。
- [ ] 更新营销 API 文档和页面调用顺序，跑营销相关测试。

## Task 3：增量效果与辅助指标

**接口：** `GroupMetrics` 增加可空 `incrementalAverageContributionProfit`、`refundRate`、`recommendedOfferUseRate`；比率分母为零时返回 `null`。

- [ ] 写指标测试：固定样本低额 `+1.50`、高额 `-1.20` 元/人；对照组无合格用户时差值为 `null`；退款和实际使用推荐券按唯一用户统计，分母为零时比率为 `null`。
- [ ] 跑 `mvn -Dtest=MarketingDemoMetricsTest test`，核对失败点为缺失字段或错误口径。
- [ ] 用 `BigDecimal` 在后端计算差值、退款率和推荐使用率；报告仍纳入未支付用户的零利润，已退款订单利润为零。更新响应和 `docs/api/marketing-demo.md`。
- [ ] 跑指标和营销完整测试，核对固定 30 人数据不变。

## Task 4：工作台页面与真实联调

**依赖：** Task 1—3 的正式 API 文档。页面保留 `/marketing-demo`，分为版本状态、试决策、实际反馈、效果复盘、下一版五区。

- [ ] 写 `web/src/services/marketingDemo.test.ts`：状态接口解包、报告新字段、空值和错误处理；先运行失败用例。
- [ ] 接入状态与报告；实验 ID 改变时清空旧报告并防止旧请求覆盖；加载页面只发 GET，不触发 `setup()`。版本卡分别显示当前运行链和待应用新版，链接到规则中心与三条流程编辑页；显式“选用当前外层版本”调用 `setup()` 后刷新。
- [ ] 单笔试决策显示 `SUCCEEDED`/`FAILED`、原因、规则命中和版本；选择成功记录后才启用反馈。逐笔详情展示推荐、实际优惠与利润；报告展示对照差值、退款率和推荐使用率，空分母显示“样本不足”。
- [ ] 运行 `npm test`、`npm run typecheck`、`npm run build`；浏览器验证首次初始化、规则新版待应用、场景选版、固定 30 人差值、单笔反馈和切换实验 ID。
- [ ] 运行后端全量测试及 `git diff --check`，更新 `docs/marketing-demo-closed-loop.md` 和 README 的实际使用步骤。

## 执行记录（2026-10-15）

- 只读状态接口从场景选中的外层不可变版本逐级追溯资格、选择与规则版本；首次读取不创建资产。结算和固定样本只在首次使用时初始化，后续由 `POST /setup` 显式选用外层当前发布版。
- 逐笔响应补充规则集、固定版本、命中行和原因码。报告补充相对对照组利润差值、退款率、推荐券实际使用率；空分母返回 `null`。
- 页面按策略状态、试决策、实际反馈、增量复盘与下一版引导组织；实验切换清空旧状态，异步报告及策略请求用序号阻止旧响应覆盖新状态。
- 独立审查发现样板初始化可能覆盖用户草稿，已改为保留并返回冲突；新增 `MarketingDemoDraftProtectionTest` 验证。业务数据仍使用当前进程内模拟反馈，预算口径未在本轮更改。
- 验证：后端 `mvn test` 108 个测试通过，前端 `npm test` 36 个测试通过，`npm run typecheck` 和 `npm run build` 通过。浏览器验证固定样本差值、实验切换、单笔规则追溯、实际原优惠 27 元、退款后 0 元、外层新版待应用与显式选用。
