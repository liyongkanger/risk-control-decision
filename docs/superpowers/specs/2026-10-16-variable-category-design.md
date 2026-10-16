# 变量中心分类设计

状态：已实现；本地 H2 与页面联调通过。

## 目标

用一个主分类帮助后台使用者查找变量。预设六类：`USER` 用户、`TRANSACTION` 交易/订单、`CAMPAIGN` 活动/实验、`BUDGET` 额度/预算、`RISK` 风险指标、`OTHER` 其他。每个变量恰属一类；来源类型和值类型保持独立。分类只影响查找与展示，不参与取值、规则判断和流程执行。

## 版本边界

分类归属属于逻辑变量的可修改元数据，按 `variableCode` 保存，不写入不可变取值版本。修改分类立即反映到当前与历史发布版本的查询响应，不产生变量新版本，也不要求重发规则或流程。新建变量可选分类；缺省归入 `OTHER`，后台页面要求显式选择。

## 已有变量迁移

Flyway 新建 `tb_variable_category`，含 `id`、唯一 `variable_code`、`category_code`、`inserttime`、`updatetime`、`isactive`。从 `tb_variable_version` 的所有不同变量码回填：`uid` 为用户；`checkoutId`、`demoOrderAmount`、`demoMarginAfterMaxOffer` 为交易/订单；`experimentId`、`lowGroupUsers`、`lowGroupUsersUnified` 为活动/实验；`demoRemainingBudget` 为额度/预算；其余为其他。未确认业务含义的旧变量不按名称猜测分类，迁移为其他。迁移不修改取值版本行。

## 接口与页面

- `GET /api/variables/categories` 返回六个 `{code,name}` 选项。
- `GET /api/variables` 保留后台分页契约，增加可选的 `categoryCode`、`sourceType`、`valueType` 精确筛选；关键词仍匹配变量码或名称。筛选变更回到第 1 页，后端查询和总数条件一致。
- 变量列表、当前版本和历史版本响应增加 `categoryCode`。`POST /api/variables` 可传 `categoryCode`；省略时默认 `OTHER`。
- `PUT /api/variables/{code}/category` 请求 `{categoryCode}`，返回当前变量详情且版本 ID、版本号不变；未知分类返回 422，变量不存在返回 404。
- 页面显示分类列与单选分类筛选，保留关键词、来源和值类型筛选；新建表单选择分类；列表行单独提供“修改分类”，与“更新版本”分开。版本更新表单不修改分类。

## 验收

测试迁移回填、分类筛选与总数、当前和历史版本同一分类、修改分类不新增版本、非法分类和不存在变量，以及页面筛选、保存和单独修改分类。联调保持既有营销样板和变量取值行为。
