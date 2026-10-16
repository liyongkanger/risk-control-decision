# 结算优惠营销闭环演示接口

状态：已实现。仅供本地样板演示；订单、支付、实验反馈保存在当前进程内存，服务重启后清空。流程资产、发布版本及场景绑定使用现有数据库。

所有接口返回 `ApiResponse<T>`：`{"code":"SUCCESS","message":"成功","data":...}`。金额是 JSON 数字，单位为元，最多两位小数。下列 `experimentId`、`checkoutId`、`userId`、`eventId` 均为 1–64 位，以英文字母开头，仅含英文字母、数字、`_`、`-`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/demo/checkout-offer/setup` | 幂等创建样板资产，并显式将场景选中外层流程当前发布版本；无请求体 |
| GET | `/api/demo/checkout-offer/status` | 只读查询当前发布资产与场景实际运行版本链；未初始化也返回成功 |
| POST | `/api/demo/checkout-offer/checkout` | 执行一次模拟结算决策 |
| POST | `/api/demo/checkout-offer/feedback` | 回传支付结算或全额退款 |
| POST | `/api/demo/checkout-offer/run-fixture` | 幂等运行固定 30 人样本并返回报告 |
| GET | `/api/demo/checkout-offer/report?experimentId=demoA` | 查询当前实验报告；空实验返回三个空组和空列表 |

## 工作台策略状态

`GET /status` 无请求体和查询参数，返回 `MarketingDemoStatusResp`。首次使用返回 HTTP 200、`initialized=false`，未建立的版本字段为 `null`；读取状态不会初始化规则、流程或场景。部分资产缺失时仍返回已有字段及 `initialized=false`。资产发布变更期间单次查询不承诺跨多张表的原子快照，刷新后可得到新状态。

已初始化时 `data` 的字段如下；实际 ID 与版本号由数据库决定：

```json
{"initialized":true,"ruleCurrentVersionId":1,
 "eligibilityCurrentVersionId":2,"eligibilityPinnedRuleVersionId":1,
 "selectionCurrentVersionId":3,"outerCurrentVersionId":4,"outerCurrentVersionNumber":1,
 "outerPinnedEligibilityVersionId":2,"outerPinnedSelectionVersionId":3,
 "sceneSelectedOuterVersionId":4,"sceneSelectedOuterVersionNumber":1,
 "runningEligibilityVersionId":2,"runningSelectionVersionId":3,"runningRuleVersionId":1}
```

`*CurrentVersionId` 表示资产当前发布版本；`*Pinned*` 表示当前发布流程固定的下游版本；`running*` 从**场景当前选中的外层版本**逐级追溯，代表下一笔结算真正使用的版本。规则、资格流程或外层流程发布新版后，`current` 可以先于 `running` 变化，这表示有新版待应用，并非运行失败。`outerCurrentVersionNumber` 与 `sceneSelectedOuterVersionNumber` 均为版本号，不能与版本 ID 混用。`initialized=true` 表示场景选中版本及其子流程和规则固定版本均可读取，不要求所有资产已经升级到最新版本。

`POST /setup` 只在固定样板资产不存在时创建并发布初版；若某个固定流程或规则已有未发布草稿，初始化会返回 HTTP 409 `VERSION_CONFLICT`、`data=null`，保留该草稿供人工处理。旧版样板流程只有在草稿仍等于当前发布图时才自动迁移到规则版。工作台的“选用当前外层版本”调用本接口，显式更新场景绑定。

## 结算请求

```json
{"experimentId":"demoA","checkoutId":"order001","userId":"user001","orderAmount":120,"preDiscountMargin":30,"existingOfferAmount":0}
```

所有字段必填。三个金额均不得为负，`existingOfferAmount` 可为 0。相同实验内重复 `checkoutId` 且请求内容相同会返回原决策及最新反馈；内容不同返回 HTTP 409。

这三个金额是模拟结算页面登记的数据，不直接进入决策流程输入。后端先按 `uid=userId + checkoutId + experimentId` 登记结算事实，再只把这些定位标识及实验组布尔值传给外层流程。`uid`、`checkoutId`、`experimentId` 均在变量中心登记为请求输入变量；资格子流程的规则直接绑定三个固定营销变量版本，营销变量的参数依赖固定到上述请求变量版本。变量来源失败使决策运行失败，不返回正常“不合格”。变量契约见[变量中心](variable-center.md)。

响应 `data` 示例：

```json
{
  "experimentId":"demoA","checkoutId":"order001","userId":"user001","group":"LOW",
  "executionId":"e36325d8-5a8e-4ec2-89ac-cfdbf76fc2c8",
  "requestId":"demoA:order001","decidedAt":"2026-10-14T07:00:00Z","outerVersionId":3,
  "subflowVersionIds":{"eligibilityCall":1,"selectionCall":2},
  "ruleSetCode":"demoOfferEligibilityRules","ruleVersionId":1,
  "matchedRuleId":null,"ruleReasonCode":"QUALIFIED",
  "decisionStatus":"SUCCEEDED","decisionConclusion":"LOW_VALUE_OFFER",
  "decisionReason":"演示实验组建议优惠","recommendedOfferCode":"LOW_5_OFF_100",
  "eligible":true,"orderAmount":120.00,"preDiscountMargin":30.00,"existingOfferAmount":0.00,
  "settled":false,"paid":false,"refunded":false,"actualOfferCode":"NONE",
  "actualDiscountAmount":0.00,"contributionProfit":0.00
}
```

`group` 为 `CONTROL`、`LOW`、`HIGH`，按 `experimentId:userId` 稳定分组。`decisionConclusion` 为 `NO_NEW_OFFER`、`LOW_VALUE_OFFER` 或 `HIGH_VALUE_OFFER`；决策失败时可为 `null`。`recommendedOfferCode` 为 `NONE`、`LOW_5_OFF_100` 或 `HIGH_18_OFF_100`。`subflowVersionIds` 记录本次实际执行的子流程版本。`ruleSetCode`、`ruleVersionId`、`matchedRuleId` 和 `ruleReasonCode` 来自资格子流程实际结果；兜底规则的 `matchedRuleId=null`，旧版或失败决策缺少规则明细时四项可为 `null`。外层流程发布后固定引用子流程版本；若子流程重新发布，需在外层草稿重新选择子流程版本并发布。

`executionId` 指向平台级决策执行记录，可在 `GET /api/decision-records/{executionId}` 查看本次输入、节点输出和子流程轨迹。营销订单的实际支付、用券、退款与利润仍由本接口的订单反馈快照提供，不写入通用执行记录。

直接调用结算或固定样本接口时，仅在演示场景尚未初始化的情况下自动执行首次 `setup`。场景已有选中版本时，结算和样本**不会**把它隐式切换为外层当前发布版本。需要应用外层新版时显式调用 `POST /setup`，或使用场景绑定接口选中外层当前版。旧结算记录仍保留原 `outerVersionId`。

## 反馈请求

支付结算：

```json
{"experimentId":"demoA","checkoutId":"order001","eventId":"paid001","eventType":"SETTLED","paid":true,"appliedOfferCode":"LOW_5_OFF_100"}
```

未支付时 `paid=false` 且 `appliedOfferCode="NONE"`。支付时可填 `NONE`、本次推荐的券码，或结算输入 `existingOfferAmount>0` 时填 `EXISTING`。回传的**实际**用券决定折扣和利润；推荐券并不等于实际使用券。退款：

```json
{"experimentId":"demoA","checkoutId":"order001","eventId":"refund001","eventType":"REFUNDED"}
```

仅已支付且未退款的订单可退款。同一结算单的相同 `eventId`、内容完全相同会返回当前快照；同一结算单的相同 `eventId` 内容不同返回 HTTP 409。不同结算单可使用相同 `eventId`。反馈响应与结算响应结构相同。

## 固定样本与报告

请求 `POST /run-fixture`：`{"experimentId":"demoA"}`。报告 `data` 为：

```json
{"experimentId":"demoA","groups":{"CONTROL":{"group":"CONTROL","users":10,"paidUsers":2,"refundedUsers":0,"merchantDiscount":0.00,"totalContributionProfit":60.00,"averageContributionProfit":6.00,"paymentConversionRate":20.00,"incrementalAverageContributionProfit":null,"refundRate":0.00,"recommendedOfferUseRate":null},"LOW":{"group":"LOW","users":10,"paidUsers":3,"refundedUsers":0,"merchantDiscount":15.00,"totalContributionProfit":75.00,"averageContributionProfit":7.50,"paymentConversionRate":30.00,"incrementalAverageContributionProfit":1.50,"refundRate":0.00,"recommendedOfferUseRate":30.00},"HIGH":{"group":"HIGH","users":10,"paidUsers":4,"refundedUsers":0,"merchantDiscount":72.00,"totalContributionProfit":48.00,"averageContributionProfit":4.80,"paymentConversionRate":40.00,"incrementalAverageContributionProfit":-1.20,"refundRate":0.00,"recommendedOfferUseRate":40.00}},"attempts":["每笔响应结构与结算响应相同"]}
```

`averageContributionProfit` 为组内总贡献利润除以合格独立用户数，`paymentConversionRate` 为百分数。`incrementalAverageContributionProfit` 是实验组人均利润减对照组人均利润；对照组或任一比较组没有合格用户时为 `null`，对照组自身始终为 `null`。`refundRate` 是发生过全额退款的唯一用户数除以曾支付的唯一用户数；`recommendedOfferUseRate` 是至少一笔未退款订单实际使用推荐券的唯一用户数除以至少获得一次新增券推荐的唯一用户数。两种比率均以百分数表示，分母为零时为 `null`。退款后支付用户和利润均从有效口径扣除。固定样本重复运行不会新增订单或反馈；策略版本切换后需换一个实验 ID 才能生成新版样本。样本呈现“高额券支付率最高、低额券人均利润最高”的预设对比，数据均为模拟数据。

## 最小联调顺序及错误

先 `setup`，再 `run-fixture`，之后 `report`；也可 `setup` → `checkout` → `feedback` → `report`。`checkout` 会自动进行幂等初始化。非法字段或反馈组合返回 HTTP 422 `INVALID_REQUEST`；重复 ID 输入冲突或非法状态变更返回 HTTP 409 `VERSION_CONFLICT`；结算不存在返回 HTTP 404 `NOT_FOUND`。这些错误的 `data` 为 `null`。无分页；报告只用于当前进程内的小规模演示样本。
