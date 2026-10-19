# 营销资格变量中心取值实施计划

状态：第一版已实施；原复选框保留实施前的步骤，实际文件与验证见末尾执行记录。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将营销资格的三个规则输入改为变量中心取值，决策请求只携带定位标识和实验组标识。

**Architecture:** 变量中心扩展受控 `DEMO_MARKETING` 来源；营销样板在决策前登记模拟订单事实。此计划最初按流程取值节点实施，后续确认改由资格规则集的输入字段固定三个变量版本并取值；已发布旧版本保持可用。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus、LiteFlow、React、Ant Design、H2、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-15-marketing-variable-source-design.md`

## 全局约束

- 遵循 `docs/standards/project-conventions.md`、`mvc-layer-conventions.md` 和 `frontend-backend-collaboration.md`；新增 Service 类和方法写中文 Javadoc。
- 已有工作区暂存与未暂存内容不重置、不提交；旧流程和变量不可变版本不删除。
- 先写失败测试，确认是目标行为缺失，再实现最小代码并复跑。后端接口及 `docs/api/` 先于前端改动。

## Task 1：受控模拟变量来源与样板事实

**Files:** `component/variable/service/{VariableService,VariableSource,VariableSourceRegistry}.java`、`HttpVariableSource.java`、`VariableServiceImpl.java`、`business/marketing/repository/MarketingDemoStore.java`、`business/marketing/service/MarketingDemoVariableSource.java`、变量 Req/Resp、`docs/api/variable-center.md`。

- [ ] 写变量服务集成测试：创建 `DEMO_MARKETING` 强类型版本，按 `uid/checkoutId/experimentId` 取订单金额与预计毛利，按实验 ID 取预算；不匹配标识、缺失事实返回来源失败；`HTTP_GET` 旧测试仍通过。
- [ ] 运行定向测试确认失败；新增来源适配接口及受控注册，变量服务根据 `sourceType` 选择来源并校验定义。营销适配器仅读取模拟仓储，不依赖 LiteFlow。
- [ ] 模拟仓储登记本次结算事实；同一结算 ID 不允许用不同事实覆盖。文档明确其进程内生命周期和来源配置字段。

## Task 2：变量化资格流程与旧样板迁移

**Files:** `business/marketing/{MarketingDemoFlows,MarketingDemoFlowService}.java`、`service/impl/MarketingDemoServiceImpl.java`、资格与集成测试、`docs/api/marketing-demo.md`、`docs/marketing-demo-closed-loop.md`。

- [ ] 写流程集成测试：资格图含三个 `get-variable`，规则节点的三个映射都是 `NODE_RESULT`；外层请求只含定位标识和实验组，原金额不进入决策输入。样板初始化创建三项变量版本并发布固定引用。
- [ ] 运行定向测试确认失败；改资格及外层图、初始化顺序和结算事实登记。`uid`、`checkoutId`、`experimentId` 均为必需输入；资格流程发布时校验变量和规则版本。
- [ ] 保留已编辑草稿；仅在旧发布图与草稿一致时自动迁移，旧决策继续使用旧版本。核对 30 人样本及反馈利润。

## Task 3：后台可选来源与联调

**Files:** `web/src/services/variableCenter.ts`、`web/src/pages/variable-center/index.tsx`、`web/src/pages/marketing-demo/`、对应测试、README 和 API 文档。

- [ ] 先补接口类型/表单测试；变量中心能展示和选择 `DEMO_MARKETING` 来源定义，编辑器继续通过组件目录和变量中心版本列表选择变量。
- [ ] 让营销工作台说明页面金额只是模拟数据登记，决策流程仅收定位标识。浏览器验证变量中心列表、资格画布映射、固定样本、单笔不同结算、反馈与退款。
- [ ] 运行 `mvn test`、`npm test`、`npm run typecheck`、`npm run build`、`git diff --check`；复核三个获取变量节点对应的来源及失败语义。

## 执行记录（2026-10-15）

- 变量中心用 `VariableSource` 扩展受控来源，现有 `HTTP_GET` 与 `DEMO_MARKETING` 共用版本、取值与画布配置契约。营销模拟数据仍只在当前进程，三个变量定义由样板初始化创建并保存在数据库。
- 最终方案：资格规则集输入字段绑定三个变量中心版本；资格流程只执行规则集并声明 `uid`、`checkoutId`、`experimentId`。实验组布尔值仍由模拟结算层生成。上方未勾选条目记录的是初版节点方案，不作为后续验收依据。
- `MarketingVariableSourceTest` 验证同一用户不同结算、强类型结果与错配失败；营销流程集成测试验证节点、输入和固定样本。多 Spring 测试上下文触发的取值事实可见性问题由决策线程临时绑定所属样板仓储处理，订单、反馈与待取值事实均保持应用上下文隔离。
- 独立审查后将 `DEMO_MARKETING` 限制为三个固定变量码与来源字段组合，预算在登记事实时形成快照。已选中旧金额直传流程或旧预算变量契约时，首次结算前自动迁移未编辑样板，并重新固定外层资格子流程版本；编辑过的草稿仍拒绝覆盖。旧预算变量不可变版本继续按原语义执行。补充旧版激活、仓储隔离和错误变量码测试。
- 最终验证：后端 `mvn test` 114 个测试通过；前端 `npm test` 36 个测试、`npm run typecheck` 与 `npm run build` 通过。本地旧 H2 样板升级预算变量到 v2、发布新版资格与外层流程后，30 人样本仍为 6.00、7.50、4.80 元/人。
