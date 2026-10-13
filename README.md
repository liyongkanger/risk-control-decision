# 风控决策平台

项目使用 Java 21、Spring Boot 4.1、LiteFlow 和 React/X6。当前可编辑结构化场景流程、保存草稿、发布版本并同步执行示例组件。

## 代码结构

`policy` 和 `decision` 按业务领域划分，领域内分别组织 `controller`、`service`、`mapper`、`vo` 等职责。`orchestrator.graph` 负责流程图定义与结构校验；`component.liteflow.compile` 生成 LiteFlow EL，节点、上下文和注册表也封装在 LiteFlow 组件包内，通过 `component.liteflow.service` 向业务模块提供能力。公共响应结构、结果码和全局异常处理放在 `common` 包。具体写法见[项目开发规范](docs/standards/project-conventions.md)和[分层开发规范](docs/standards/mvc-layer-conventions.md)。

## 本地配置

默认启用 `local` profile。参考 `collection-agent` 的配置组织方式，MySQL 和 Redis 的本地连接参数放在 `application.yml` 的 `local` 配置段中；真实连接参数仍从环境变量读取，不在仓库保存凭据。其他环境需提供自己的 profile 配置。

| 环境变量 | 默认值 |
|---|---|
| `RISK_DB_URL` | `jdbc:mysql://localhost:3306/risk_control_decision?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai` |
| `RISK_DB_USERNAME` | `root` |
| `RISK_DB_PASSWORD` | 空 |
| `RISK_REDIS_HOST` | `localhost` |
| `RISK_REDIS_PORT` | `6379` |
| `RISK_REDIS_PASSWORD` | 空 |
| `RISK_REDIS_DATABASE` | `0` |

当前由 Spring Boot 管理的 MySQL Connector/J 为 9.7，目标 MySQL Server 8.0 及以上；部署版本确定后再核对驱动兼容性。

准备好 MySQL 数据库后，使用 JDK 21 运行 `mvn spring-boot:run`。Flyway 会执行 `V1__scene_flow.sql` 创建场景、草稿和版本表。Redis 已配置，但当前流程切片不依赖 Redis。Actuator 提供 `/actuator/health` 和 `/actuator/info`。

## 前端开发

```powershell
cd web
npm ci
npm run dev
```

打开 `http://localhost:5173/`。Vite 将 `/api` 请求转发到本地 `8080` 端口。页面初始提供一条 `开始 → emit-value → 结束` 示例流程。输入场景码后，可以校验、保存草稿、发布版本和执行示例。

## 场景流程

图数据契约见[设计文档](docs/superpowers/specs/2026-10-12-scene-flow-model-design.md)。首期支持 `START`、`END`、`COMPONENT`、`PARALLEL_SPLIT`、`PARALLEL_JOIN`、`CONDITION` 和 `CONDITION_JOIN`。并行汇合等待所有分支。组件节点和条件节点的失败策略只能是 `FAIL_FAST`。草稿可暂不完整，发布时必须通过全部校验；单图限制为 200 个节点、500 条边和 1 MiB JSON。HTTP 写入请求也限制为 1 MiB。

首期组件目录只有无外部副作用的 `emit-value`（配置 `{"value": 任意非空值}`）和 `is-positive`（配置 `{"inputKey": "输入字段名"}`）。场景码必须以字母开头，只包含字母、数字、下划线和连字符，最长 64 位。执行接口返回流程状态、节点结果和轨迹；业务决策值尚未定义。

发布流程先写入不可变版本，再加载和验证 LiteFlow 链，最后在数据库中按草稿修订号与旧发布指针进行条件切换。加载或验证失败的版本保留为未发布记录，不会被场景请求选中；重试会生成新的版本号。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/scenes/{sceneCode}/draft` | 读取草稿及修订号 |
| `PUT` | `/api/scenes/{sceneCode}/draft?expectedRevision=0` | 保存草稿并检查修订号 |
| `POST` | `/api/scenes/{sceneCode}/validate` | 校验图并预览 EL |
| `POST` | `/api/scenes/{sceneCode}/publish` | 用 `{"expectedRevision":1}` 发布不可变版本 |
| `POST` | `/api/decisions/evaluate` | 用 `{"sceneCode":"demoScene","input":{}}` 同步执行 |

业务接口统一返回 `{ "code": "SUCCESS", "message": "成功", "data": ... }`；失败时保留对应 HTTP 状态，`code` 为稳定结果码，`data` 可为空。`/validate` 的图校验结果在 `data.issues`，其他操作抛出的图结构错误在 `data` 问题列表中，均提供节点和连线定位信息。

## 验证范围

后端 `mvn test` 使用 H2 的 MySQL 兼容模式验证数据库操作；前端使用 `npm test` 与 `npm run build`。浏览器已验证校验、保存、发布、执行及错误定位。真实 MySQL 联调尚未完成，H2 测试不能证明 MySQL 版本兼容。当前发布与执行设计针对单个应用实例，管理接口尚无鉴权，暂不用于生产环境。
