# 通用决策执行记录设计

## 目标

每次场景入口决策执行生成独立 `executionId`，保存请求快照、固定版本、业务结果、节点结果和节点轨迹。后台可分页查找并查看一次执行；营销样板通过该 ID 跳转到同一详情。

## 契约

- `POST /api/decisions/evaluate` 响应增加 `executionId`；相同 `requestId` 的两次执行仍有不同 `executionId`。
- `GET /api/decision-records?page=&pageSize=&sceneCode=&requestId=&status=` 返回 `{records,total,page,pageSize}`，按记录 ID 倒序。
- `GET /api/decision-records/{executionId}` 返回一次执行的请求输入、执行结果、节点结果、轨迹和创建时间。不存在返回标准 404。
- 子流程调用留在父流程的 `nodeResults` 与 `childTrace` 中，不额外生成顶层记录。
- 营销订单响应增加 `executionId`，供平台详情跳转。实际支付、用券、退款仍由营销业务保存，不进入通用执行快照。

## 页面

新增“决策记录”菜单。列表可按场景、请求标识与执行状态筛选，后端分页。详情先显示最终结果、失败原因和固定版本，再按实际 `trace` 显示节点及嵌套子流程轨迹，节点结果可展开查看。输入与技术原文折叠展示。

## 边界

记录只覆盖进入执行阶段的场景入口调用；场景不存在等执行前校验失败不生成记录。当前开发阶段保存完整输入与节点快照；接入生产数据前需确定敏感字段处理与保留期限。
