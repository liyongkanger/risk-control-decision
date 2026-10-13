# 风控决策平台

项目使用 Java 21、Spring Boot 4.1、MyBatis-Plus、LiteFlow 和 React/X6。当前可编辑结构化场景流程、保存草稿、发布版本并同步执行示例组件。

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

准备好 MySQL 数据库后，使用 JDK 21 运行 `mvn spring-boot:run`。Flyway 依次执行历史 V1、规范表结构 V2、场景元数据 V3 和流程资产独立化 V4 迁移；已经执行过的版本不会再次执行。Redis 已配置，但当前流程切片不依赖 Redis。Actuator 提供 `/actuator/health` 和 `/actuator/info`。

## 后台开发

```powershell
cd web
npm ci
npm run dev
```

打开 `http://localhost:8000/`，进入 Ant Design Pro 后台的场景管理页。Umi Max 将 `/api` 请求转发到本地 `8080` 端口。首版使用静态菜单和展示用模拟用户；场景数据来自真实后端。先新建场景，再点击“设计流程”。新场景画布初始只有开始、结束节点；需要示例流程时点击“载入示例模板”，随后可保存草稿、校验、发布和试运行。

场景与流程资产已分离：新建场景会创建并绑定一条入口流程；“流程资产”菜单可独立新建、搜索、设计和发布流程。“场景管理”显示入口流程，可将同一已发布流程绑定给多个场景；流程后续发布新版本不会自动切换已绑定场景。

本次隔离环境的页面验收步骤和结果见[场景与流程隔离的本地页面联调](docs/references/local-h2-flow-ui-integration-2026-10-13.md)。

需要查看所有节点类型的组合示例，可在画布中点击“载入全节点示例”；示例节点含义及隔离环境中的 `test` 流程见[全节点 test 流程说明](docs/references/all-node-test-flow.md)。

`npm test` 运行前端行为测试，`npm run typecheck` 检查 TypeScript，`npm run build` 生成后台静态资源。部署静态资源时，服务端需将非资源路径回退到 `index.html`，以支持直接访问 `/scenes/{sceneCode}/flow`。

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
| `GET` | `/api/scenes?keyword=&page=1&pageSize=20` | 搜索场景并分页 |
| `POST` | `/api/scenes` | 新建场景 |
| `GET` | `/api/scenes/{sceneCode}` | 场景详情与当前发布版本 |
| `PUT` | `/api/scenes/{sceneCode}` | 修改场景名称和描述 |
| `PUT` | `/api/scenes/{sceneCode}/entry-flow` | 绑定独立流程，并选中其当前发布版本 |
| `POST` | `/api/flows` | 创建脱离场景的流程资产 |
| `GET` | `/api/flows?keyword=&page=1&pageSize=20` | 分页搜索独立流程资产 |
| `GET` | `/api/flows/{flowCode}` | 读取流程资产 |
| `GET/PUT` | `/api/flows/{flowCode}/draft` | 读取或保存独立流程草稿 |
| `POST` | `/api/flows/{flowCode}/validate` | 校验独立流程图 |
| `POST` | `/api/flows/{flowCode}/publish` | 发布独立流程版本 |

业务接口统一返回 `{ "code": "SUCCESS", "message": "成功", "data": ... }`；失败时保留对应 HTTP 状态，`code` 为稳定结果码，`data` 可为空。`/validate` 的图校验结果在 `data.issues`，其他操作抛出的图结构错误在 `data` 问题列表中，均提供节点和连线定位信息。

## 验证范围

后端 `mvn test` 使用 H2 的 MySQL 兼容模式验证数据库操作；前端使用 `npm test` 与 `npm run build`。浏览器已验证场景管理、校验、保存、发布、执行及错误定位。[本地 MySQL 联调记录](docs/references/local-mysql-integration-2026-10-13.md)覆盖 Flyway V1→V3、完整接口链路及重启恢复；实测服务器为 MySQL 5.7.40，目标 MySQL 8 及以上仍需验证。本机 Redis 未运行，因此 Actuator 总体健康状态为 DOWN。当前发布与执行设计针对单个应用实例，管理接口尚无鉴权，暂不用于生产环境。
