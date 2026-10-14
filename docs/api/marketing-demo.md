# 结算优惠营销闭环演示接口

状态：已实现。仅供本地样板演示；订单、支付、实验反馈保存在当前进程内存，服务重启后清空。流程资产、发布版本及场景绑定使用现有数据库。

所有接口返回 `ApiResponse<T>`：`{"code":"SUCCESS","message":"成功","data":...}`。金额是 JSON 数字，单位为元，最多两位小数。下列 `experimentId`、`checkoutId`、`userId`、`eventId` 均为 1–64 位，以英文字母开头，仅含英文字母、数字、`_`、`-`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/demo/checkout-offer/setup` | 幂等创建并发布两条子流程、一条外层流程，创建及绑定演示场景；无请求体 |
| POST | `/api/demo/checkout-offer/checkout` | 执行一次模拟结算决策 |
| POST | `/api/demo/checkout-offer/feedback` | 回传支付结算或全额退款 |
| POST | `/api/demo/checkout-offer/run-fixture` | 幂等运行固定 30 人样本并返回报告 |
| GET | `/api/demo/checkout-offer/report?experimentId=demoA` | 查询当前实验报告；空实验返回三个空组和空列表 |

## 结算请求

```json
{"experimentId":"demoA","checkoutId":"order001","userId":"user001","orderAmount":120,"preDiscountMargin":30,"existingOfferAmount":0}
```

所有字段必填。三个金额均不得为负，`existingOfferAmount` 可为 0。相同实验内重复 `checkoutId` 且请求内容相同会返回原决策及最新反馈；内容不同返回 HTTP 409。

响应 `data` 示例：

```json
{
  "experimentId":"demoA","checkoutId":"order001","userId":"user001","group":"LOW",
  "requestId":"demoA:order001","decidedAt":"2026-10-14T07:00:00Z","outerVersionId":3,
  "subflowVersionIds":{"eligibilityCall":1,"selectionCall":2},
  "decisionStatus":"SUCCEEDED","decisionConclusion":"LOW_VALUE_OFFER",
  "decisionReason":"演示实验组建议优惠","recommendedOfferCode":"LOW_5_OFF_100",
  "eligible":true,"orderAmount":120.00,"preDiscountMargin":30.00,"existingOfferAmount":0.00,
  "settled":false,"paid":false,"refunded":false,"actualOfferCode":"NONE",
  "actualDiscountAmount":0.00,"contributionProfit":0.00
}
```

`group` 为 `CONTROL`、`LOW`、`HIGH`，按 `experimentId:userId` 稳定分组。`decisionConclusion` 为 `NO_NEW_OFFER`、`LOW_VALUE_OFFER` 或 `HIGH_VALUE_OFFER`；决策失败时可为 `null`。`recommendedOfferCode` 为 `NONE`、`LOW_5_OFF_100` 或 `HIGH_18_OFF_100`。`subflowVersionIds` 记录本次实际执行的子流程版本。外层流程发布后固定引用子流程版本；若子流程重新发布，需在外层草稿重新选择子流程版本并发布。

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
{"experimentId":"demoA","groups":{"CONTROL":{"group":"CONTROL","users":10,"paidUsers":2,"refundedUsers":0,"merchantDiscount":0.00,"totalContributionProfit":60.00,"averageContributionProfit":6.00,"paymentConversionRate":20.00},"LOW":{"group":"LOW","users":10,"paidUsers":3,"refundedUsers":0,"merchantDiscount":15.00,"totalContributionProfit":75.00,"averageContributionProfit":7.50,"paymentConversionRate":30.00},"HIGH":{"group":"HIGH","users":10,"paidUsers":4,"refundedUsers":0,"merchantDiscount":72.00,"totalContributionProfit":48.00,"averageContributionProfit":4.80,"paymentConversionRate":40.00}},"attempts":["每笔响应结构与结算响应相同"]}
```

`averageContributionProfit` 为组内总贡献利润除以合格独立用户数，`paymentConversionRate` 为百分数。退款后支付用户和利润均从有效口径扣除。固定样本重复运行不会新增订单或反馈。样本呈现“高额券支付率最高、低额券人均利润最高”的预设对比，数据均为模拟数据。

## 最小联调顺序及错误

先 `setup`，再 `run-fixture`，之后 `report`；也可 `setup` → `checkout` → `feedback` → `report`。`checkout` 会自动进行幂等初始化。非法字段或反馈组合返回 HTTP 422 `INVALID_REQUEST`；重复 ID 输入冲突或非法状态变更返回 HTTP 409 `VERSION_CONFLICT`；结算不存在返回 HTTP 404 `NOT_FOUND`。这些错误的 `data` 为 `null`。无分页；报告只用于当前进程内的小规模演示样本。
