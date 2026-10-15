# 后台可配置规则集实施计划

状态：第一版已实施。以下复选框保留实施前的细化步骤；实际文件拆分、验证与已知边界见末尾执行记录。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让后台能编辑、试算和发布规则集，并通过通用规则组件把固定规则版本用于营销资格决策。

**Architecture:** `rule` 领域保存规则集草稿与不可变版本并执行纯计算规则表；`component.liteflow` 仅适配规则节点；`policy` 在发布流程时校验并固定引用。规则中心管理业务规则，组件管理展示通用规则执行能力，营销样板提供端到端验收。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus、Flyway、LiteFlow、React、Ant Design、Vitest、H2 MySQL 兼容模式。

**Spec:** `docs/superpowers/specs/2026-10-15-configurable-rule-set-design.md`

## 全局约束

- 先读 `docs/standards/project-conventions.md`、`mvc-layer-conventions.md`、`admin-list-pagination.md`、`frontend-backend-collaboration.md`；按后端接口及 `docs/api/` 文档先行，再接前端。
- 新 Java 类、接口、枚举及对外 Service 方法写中文 Javadoc；不用 `record`、`var`、`instanceof` 模式变量。
- 业务表采用 `tb_`、`id BIGINT`、`inserttime`、`updatetime`、`isactive`；Flyway 新增迁移，不修改已应用的 V1—V6。
- 规则是纯计算；输入 `STRING`、`BOOLEAN`、`DECIMAL`，同行 `AND`，按优先级首命中且必须有兜底；失败不能伪装成业务拒绝。
- 流程发布固定规则版本；已发布流程不随规则发布变化。当前请求输入无类型声明，发布仅校验字段存在，执行时检查类型。
- 当前工作区已有大量暂存及未暂存改动。实施前记录状态，不清理、不重置；提交时只纳入本任务路径并依照 `docs/standards/commit-conventions.md` 检查提交内容。

## 文件与职责

| 文件或目录 | 职责 |
| --- | --- |
| `src/main/java/com/dmp/riskdecision/rule/model/` | 规则集、条件、结果、试算值对象；不依赖 LiteFlow |
| `src/main/java/com/dmp/riskdecision/rule/service/` | 草稿、版本、试算与执行契约及实现 |
| `src/main/java/com/dmp/riskdecision/rule/mapper/`、`src/main/resources/mapper/rule/` | 持久化行和条件 SQL |
| `src/main/resources/db/migration/V7__rule_sets.sql` | 规则集、草稿、不可变版本表 |
| `src/main/java/com/dmp/riskdecision/rule/controller/`、`vo/`、`assembler/` | 后台 API、请求校验、响应转换 |
| `src/main/java/com/dmp/riskdecision/component/liteflow/node/rule/` | `execute-rule-set` 和规则结果条件的 LiteFlow 适配 |
| `src/main/java/com/dmp/riskdecision/policy/service/impl/FlowComponentValidationService.java` | 流程发布时校验规则引用、映射、上游可达性 |
| `web/src/pages/rule-center/`、`web/src/services/ruleCenter.ts` | 规则中心页面和 API 封装 |
| `web/src/flow/RuleSetNodeInspector.tsx`、`RuleResultInspector.tsx` | 画布规则引用与结果判断表单 |
| `src/main/java/com/dmp/riskdecision/business/marketing/` | 演示规则初始化、资格流程与结算输入迁移 |
| `docs/api/rule-center.md`、`docs/api/component-catalog.md`、`docs/marketing-demo-closed-loop.md` | 实际接口、组件目录、演示说明 |

## Task 1：规则领域模型、存储和纯计算引擎

**产出接口：** `RuleSetService#create(code,name,definition)`、`updateDraft(code,expectedRevision,definition)`、`publish(code,expectedRevision)`、`currentVersion(code)`、`requireVersion(id)`、`evaluateVersion(id,inputs)`；`RuleEvaluation` 返回 `resultCode`、`reasonCode`、`reason`、可空 `matchedRuleId`、规则集码和版本 ID。`RuleDefinition` 包含 `inputs`、`resultCodes`、有序 `rows`、`defaultResult`。

**Files:** Create `rule/model/{RuleDefinition,RuleInput,RuleCondition,RuleRow,RuleOutcome,RuleEvaluation,RuleSetVersion}.java`、`rule/service/{RuleSetService,RuleEvaluator}.java`、`rule/service/impl/RuleSetServiceImpl.java`、`rule/mapper/{RuleSetRow,RuleDraftRow,RuleVersionRow,RuleSetMapper,RuleDraftMapper,RuleVersionMapper}.java`、`src/main/resources/mapper/rule/*.xml`、`src/main/resources/db/migration/V7__rule_sets.sql`；Test `src/test/java/com/dmp/riskdecision/rule/RuleEvaluatorTest.java`、`RuleSetServiceTest.java`。

- [ ] 写 `RuleEvaluatorTest`：优先级 10、20 同时满足时返回 10；同行两个条件必须同时满足；无命中返回兜底及 `matchedRuleId=null`；缺字段、类型错误抛明确异常；DECIMAL 用 `BigDecimal` 比较。
- [ ] 跑 `mvn -Dtest=RuleEvaluatorTest test`，确认因类缺失失败。
- [ ] 实现 `RuleEvaluator#evaluate(RuleSetVersion, Map<String,Object>)`：按已排序优先级遍历，每行用 `allMatch` 判断条件，首命中构造结果；兜底构造结果。类型检查在比较前完成，不做隐式字符串转数字。
- [ ] 写 `RuleSetServiceTest`：创建后草稿修订号为 1；错误修订号拒绝保存；重复优先级、未声明字段、不兼容操作符、无兜底或未声明结果码不能发布；发布 V1 后改草稿发布 V2，V1 试算结果不变；并发发布只有一个指针生效。
- [ ] 跑 `mvn -Dtest=RuleSetServiceTest test`，确认失败；按三张表实现元数据、草稿 JSON、不可变版本 JSON。发布在事务内校验草稿修订号、写版本、条件切换当前版本指针；失败时原指针不变。
- [ ] 跑上述两个定向测试及 `mvn test`。检查 V7 在 H2 MySQL 兼容模式能迁移，旧 V1—V6 不变。

模型字段约定示意：

```json
{"inputs":[{"name":"orderAmount","type":"DECIMAL"}],"resultCodes":["ELIGIBLE","INELIGIBLE"],"rows":[{"id":"min-order","priority":10,"conditions":[{"input":"orderAmount","operator":"LT","expected":100}],"result":{"resultCode":"INELIGIBLE","reasonCode":"ORDER_BELOW_THRESHOLD","reason":"订单金额不足"}}],"defaultResult":{"resultCode":"ELIGIBLE","reasonCode":"QUALIFIED","reason":"满足资格"}}
```

## Task 2：规则中心后端 API 与契约

**依赖：** Task 1 的 `RuleSetService`。**产出接口：** `GET /api/rule-sets` 分页，`POST /api/rule-sets` 创建，`GET /api/rule-sets/{code}` 详情，`PUT /api/rule-sets/{code}/draft` 保存，`POST /api/rule-sets/{code}/test` 试算，`POST /api/rule-sets/{code}/publish` 发布，`GET /api/rule-sets/versions/{id}` 固定版本。

**Files:** Create `rule/controller/RuleSetController.java`、`rule/vo/req/{CreateRuleSetReq,UpdateRuleDraftReq,TestRuleSetReq,PublishRuleSetReq}.java`、`rule/vo/resp/{RuleSetResp,RuleSetPageResp,RuleEvaluationResp}.java`、`rule/assembler/RuleSetAssembler.java`、`docs/api/rule-center.md`；Modify `common/exception/GlobalExceptionHandler.java` and `common/response/ResultCode.java` only for missing stable error mappings; Test `src/test/java/com/dmp/riskdecision/rule/RuleSetApiTest.java`。

- [ ] 写 API 测试：创建 → 保存 → 试算草稿 → 发布 → 查询版本；分页总数和空列表；错误修订号返回 HTTP 409；不合法规则返回 HTTP 422；未知规则码或版本返回 HTTP 404；失败响应 `data=null`。
- [ ] 跑 `mvn -Dtest=RuleSetApiTest test`，确认失败。
- [ ] 实现 Controller、Req/Resp 和 Assembler。保存请求携带 `expectedRevision`，发布请求携带 `expectedRevision`；重试只在预期修订号匹配时生效，成功后返回最新修订号或版本 ID。
- [ ] 写 `docs/api/rule-center.md`，逐字段写请求、成功/失败响应、分页、`null`、版本和调用顺序；核对 API 测试中的实际 JSON。
- [ ] 跑定向 API 测试和 `mvn test`；以文档为前端交接依据。

## Task 3：通用规则组件、结果条件与流程发布校验

**依赖：** Task 1 的 `currentVersion`、`requireVersion`、`evaluateVersion`；Task 2 的契约。**产出：** `execute-rule-set` 普通组件与 `rule-result-equals` 条件组件。规则节点配置约定 `ruleSetCode`、`ruleVersionId`、`inputMappings`；映射值使用 `{source:"INPUT",key:"orderAmount"}` 或 `{source:"NODE_RESULT",nodeId:"margin",path:""}`，仅允许已经验证的强类型标量来源。条件配置 `sourceNodeId`、`expectedResultCode`。

**Files:** Create `component/liteflow/node/rule/{ExecuteRuleSetNodeAdapter,RuleResultEqualsNodeAdapter}.java`；Modify `component/liteflow/registry/ComponentRegistry.java`、`policy/service/impl/FlowComponentValidationService.java`、`docs/api/component-catalog.md`；Test `src/test/java/com/dmp/riskdecision/component/liteflow/RuleSetComponentTest.java`、`src/test/java/com/dmp/riskdecision/policy/RuleSetFlowPublicationTest.java`。

- [ ] 写组件测试：节点读取流程输入及上游变量结果，输出含固定规则版本和命中行；缺输入或类型错误使流程失败，不转为 `INELIGIBLE`。条件节点只比较已执行规则节点的 `resultCode`。
- [ ] 写发布测试：规则版本过期、规则码与版本不符、映射漏字段、未声明请求输入、不可达上游节点、变量值类型不符、未知结果码分别拒绝；正确配置发布后，规则集发布新版不改变旧流程结果。
- [ ] 跑两个定向测试确认失败；在注册表加入两个组件定义，并实现 LiteFlow 适配器。通过 `RuleSetService` 调用领域能力，适配器不解析规则行。
- [ ] 扩展 `FlowComponentValidationService` 做以上校验；对请求输入只校验 `requiredInputs` 中声明，运行时再核对值类型。不要复用任意 JSON 路径读取规则结果。
- [ ] 跑定向测试及 `mvn test`，更新组件目录契约和输出字段说明。

## Task 4：规则中心页面与画布配置

**依赖：** Task 2 已实现并核对的 `docs/api/rule-center.md`；Task 3 组件目录与节点契约。**产出：** 可搜索规则集、编辑行与兜底、试算、发布；画布可选择规则固定版本并配置输入映射与结果判断。

**Files:** Create `web/src/services/ruleCenter.ts`、`web/src/services/ruleCenter.test.ts`、`web/src/pages/rule-center/index.tsx`、`web/src/flow/{RuleSetNodeInspector,RuleResultInspector}.tsx`、`web/src/flow/ruleSetConfig.ts`、`web/src/flow/ruleSetConfig.test.ts`；Modify `web/config/routes.ts`、`web/src/flow/FlowEditor.tsx`、`web/src/flow/componentOptions.ts`、`web/src/pages/components/index.tsx`。页面交互用浏览器验证；当前前端没有 React 组件测试库。

- [ ] 写 API 解包与纯配置逻辑测试：分页查询、空列表、版本列表、409 冲突信息；节点配置生成与映射缺失提示。页面草稿脏状态、试算命中行与发布版本展示放入浏览器验收。
- [ ] 在 `web` 目录跑 `npm test`，确认新用例失败。
- [ ] 实现服务层封装后做规则中心列表、详情、规则表编辑、试算和发布；按钮状态与失败信息以真实响应为准。组件管理增加“规则组件”分类入口，只展示能力契约，不复制规则集编辑页。
- [ ] 画布新增专用 Inspector；选择规则集当前版本后根据输入声明生成映射字段，选择结果码时从该版本的 `resultCodes` 中取值；保存节点仍使用图 JSON 契约。
- [ ] 跑 `npm test`、`npm run typecheck`、`npm run build`；用浏览器验证创建、试算、发布、画布保存与关键错误路径。

## Task 5：营销样板迁移与端到端验收

**依赖：** Task 1—4。**产出：** 资格由规则集计算，订单反馈仍由模拟结算负责；固定样本继续可重复运行。

**Files:** Modify `business/marketing/{MarketingDemoFlows,MarketingDemoFlowService}.java`、`business/marketing/service/impl/MarketingDemoServiceImpl.java`、必要的样板初始化脚本、`docs/marketing-demo-closed-loop.md` 和 `README.md`；Test `src/test/java/com/dmp/riskdecision/business/marketing/MarketingRuleSetIntegrationTest.java` 及现有营销测试。

- [ ] 写集成测试：初始化固定规则集与资格流程；订单不足、利润不足、预算不足、合格四种输入返回正确原因；同一规则版本下固定 30 人样本的三组人均利润仍为 6.00、7.50、4.80；支付、退款反馈不由规则组件执行。
- [ ] 跑定向测试确认失败；样板初始化按“规则版本 → 资格流程 → 优惠选择流程 → 外层流程 → 场景绑定”的顺序发布，显式记录依赖版本。
- [ ] 将 `MarketingDemoServiceImpl` 中的资格布尔预判替换为订单与预算事实准备，资格结论由规则节点产生；对固定券额计算出的 `marginAfterMaxOffer` 和 `remainingBudget` 继续标注为样板准备逻辑。
- [ ] 核对预算口径：若保留现状，文档明确包含已有优惠；若按业务基线调整，仅累计实际使用的新增券并同步更新测试。不得让规则执行器隐式修改这个口径。
- [ ] 跑 `mvn test`、前端测试、类型检查与构建；实际后端和页面各跑一条规则命中、兜底、版本隔离、失败与营销反馈链路。更新演示文档中的新时序和运行步骤。

## 完成判定

五项任务的定向及全量验证通过，`docs/api/rule-center.md` 与实际响应一致，已发布流程在规则集更新后结果不变，营销样板能在后台调整规则并以新版流程验证结果。提交前按提交规范检查暂存区，仅纳入本轮变更。

## 执行记录（2026-10-15）

- 数据模型实际使用 `tb_rule_set` 保存元数据与草稿、`tb_rule_set_version` 保存不可变版本；草稿修订号和发布指针由条件更新保护。没有单独的草稿表。
- 领域与 HTTP 行为用 `RuleEvaluatorTest`、`RuleSetApiTest` 验证；组件和固定版本用 `RuleFlowIntegrationTest` 验证。测试按可观察行为组合，未逐层建立同名 `RuleSetServiceTest` 和 `RuleSetComponentTest`。
- 后台列表只返回规则集摘要；详情再加载完整规则表，避免分页列表传输每套规则的全部行。规则中心和画布使用专门表单，组件管理新增取值、判断、规则三个二级分类。
- 营销资格规则集由样板初始化，预算仍按原有的实际优惠累计口径准备快照；已发布资格流程和外层流程固定引用版本。旧演示流程首次初始化时会发布规则版新流程版本，历史版本保留。
- 独立代码审查指出历史版本试算和十进制精度问题，已补固定版本试算接口与页面，并在 JSON 请求、存储和后台表单端保留精确十进制文本。营销子流程的结果明细包含规则版本与命中行。
- 验证：后端 `mvn test` 103 个测试通过；前端 `npm test` 34 个测试通过，`npm run typecheck`、`npm run build` 通过。浏览器完成规则集创建、规则行编辑、草稿及发布版本试算、发布、画布选版、组件分类及 30 人营销样本核对。
