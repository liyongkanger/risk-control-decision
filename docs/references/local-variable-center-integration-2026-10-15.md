# 变量中心第一版本地联调

日期：2026-10-15。后端使用 JDK 21、Spring Boot test profile、文件型 H2 `.local/variable-review.mv.db` 和端口 8081；前端 Umi Max 使用端口 8001 并代理 `/api`。原 `.local/review.mv.db` 未删除，未作为新模型数据来源。

## 已验证链路

- 通过 `/api/demo/checkout-offer/setup` 重建固定营销样板；新库中有 4 条演示流程资产和 1 个场景。
- 变量中心页面创建 `lowGroupUsers`（`DECIMAL`），来源为本地演示报告 HTTP GET，输入 `experimentId`，取值路径为 `data.groups.LOW.users`。运行固定 30 人样本后，页面“试取值”显示 `DECIMAL · 10 · 版本 ID 1`。
- 流程设计页左侧仅显示一种取数节点“获取变量”。`scripts/init-variable-review.ps1` 创建并发布 `variableCanvasDemo`：`获取变量 lowGroupUsers → 比较变量 ≥10 → 业务结果`，绑定 `variableCanvasScene` 后实测决策为 `SUCCEEDED / ENOUGH`。在画布中选择上游获取变量节点时，“比较变量”表单自动识别 `DECIMAL` 类型。
- 后端重启后先查询到 5 条流程资产及 1 个变量，说明新文件型 H2 数据得以保留；随后新增并发布变量样板流程。旧库文件仍在 `.local/` 中。
- 后端全量 `mvn test`：93 项通过；前端 `npm test`：31 项通过，`npm run typecheck` 和 `npm run build` 通过。后端集成测试覆盖三种强类型、精确数值比较、变量版本固定、发布不请求来源、来源失败及响应头后停发数据的超时。
- 评审修正后，已移除的 `/api/rpc-operations` 返回 HTTP 404；变量来源返回 422 时，试取值接口返回 HTTP 502 `VARIABLE_RESOLUTION_FAILED`。数据库唯一键竞争映射为版本冲突。

## 边界

本轮直接移除旧 RPC 操作 API 和 `http-json-get`。V5 迁移作为已应用历史保留，V6 建立变量表并删除旧操作表；开发验收使用新的文件型数据库。变量来源第一版只支持已允许主机上的 HTTP GET，响应必须为 JSON 对象。演示订单反馈仍在进程内，重启后可重新运行固定样本。
