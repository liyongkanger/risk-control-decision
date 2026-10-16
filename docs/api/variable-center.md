# 变量中心与决策取值接口

状态：已实现。变量中心在当前应用内运行；来源支持 `REQUEST_INPUT` 请求输入、`FLOW_CONTEXT` 当前流程节点结果、受控只读 `HTTP_GET`，以及固定营销样板的 `DEMO_MARKETING` 进程内模拟来源，输出一个 `STRING`、`BOOLEAN` 或 `DECIMAL` 值。通用数据库查询来源、写操作、认证、重试与默认值不在本版范围内。

## 变量定义与版本

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/variables?keyword=&page=1&pageSize=10` | 按变量码或名称分页查询当前版本 |
| `GET` | `/api/variables/{code}` | 获取当前版本 |
| `GET` | `/api/variables/versions/{id}` | 获取指定不可变版本 |
| `POST` | `/api/variables` | 创建变量首版 |
| `PUT` | `/api/variables/{code}` | 基于预期版本号创建新版 |
| `POST` | `/api/variables/{code}/test` | 使用当前版本和模拟输入试取值 |
| `GET` | `/api/variables/categories` | 获取预设业务分类 `{code,name}[]` |
| `PUT` | `/api/variables/{code}/category` | 修改变量分类，不创建取值版本 |

## 业务分类与筛选

每个逻辑变量归属一个业务分类。分类码固定为：`USER` 用户、`TRANSACTION` 交易/订单、`CAMPAIGN` 活动/实验、`BUDGET` 额度/预算、`RISK` 风险指标、`OTHER` 其他。分类与 `sourceType`（取值来源）及 `valueType`（返回类型）分别表示不同维度。

`GET /api/variables?keyword=&categoryCode=&sourceType=&valueType=&page=1&pageSize=10` 在服务端按可选条件筛选，响应仍为 `{records,total,page,pageSize}`，其中 `total` 是全部筛选条件下的总数。`records`、`GET /api/variables/{code}` 与历史版本 `GET /api/variables/versions/{id}` 均增加 `categoryCode`。分类是按变量码保存的可修改元数据；同一变量的历史版本查询也展示其**当前分类**，不会回写不可变版本行。

新建变量请求可附 `"categoryCode":"TRANSACTION"`；省略时归入 `OTHER`。独立修改分类使用 `PUT /api/variables/{code}/category`，请求体为 `{"categoryCode":"USER"}`，成功 `data` 为更新后当前变量详情；其 `id` 和 `versionNumber` 不变，已发布规则和流程无需重发。未知分类为 HTTP 422 `INVALID_REQUEST`，变量不存在为 HTTP 404 `NOT_FOUND`。

Flyway V9 为已有变量回填分类：`uid`→用户；`checkoutId`、`demoOrderAmount`、`demoMarginAfterMaxOffer`→交易/订单；`experimentId`、`lowGroupUsers`、`lowGroupUsersUnified`→活动/实验；`demoRemainingBudget`→额度/预算；其余→其他。迁移不更改变量取值版本。

创建请求：

```json
{"variableCode":"userMargin","variableName":"用户订单毛利","valueType":"DECIMAL","sourceType":"HTTP_GET","url":"http://localhost:9199/margin","timeoutMs":1000,"parameterNames":["userId"],"dependencyVersionIds":{"userId":11},"resultPath":"data.margin"}
```

示例要求变量中心先创建 `userId`，来源为 `REQUEST_INPUT`、类型为 `STRING`，且其版本 ID 为 `11`。`dependencyVersionIds` 必须覆盖外部变量的全部 `parameterNames`，每个 ID 必须属于同名变量；定义和运行均不追随依赖变量后续的新版本。请求输入和流程上下文来源不配置 URL、结果路径或依赖；请求输入变量码与流程输入字段名相同。

更新请求保留除 `variableCode` 外的字段，并增加 `expectedVersion`。更新产生新版本 ID 和版本号；旧版本不变。成功响应 `data` 为 `{id,variableCode,variableName,versionNumber,valueType,sourceType,url,timeoutMs,parameterNames,dependencyVersionIds,resultPath}`。分页响应的 `data` 为 `{records,total,page,pageSize}`；`page>=1`，`1<=pageSize<=100`。

试取值请求：`{"input":{"userId":"user123"}}`。成功 `data` 为 `{variableCode,versionId,valueType,value}`，其中 `value` 是一个与 `valueType` 对应的 JSON 标量。试取 `FLOW_CONTEXT` 时，`input` 中同名字段是临时样例；正式流程必须使用图中声明的生产节点绑定，不会从请求同名字段回退。HTTP 响应必须为 JSON 对象，`resultPath` 指向其中一个非空字段；原始响应对象不暴露给决策流程。缺字段、类型不符、超时、非 2xx 和过大响应均使取值失败。

变量码为以字母开头的 1–64 位字母、数字、下划线或连字符；参数名最多 20 个且不可重复。超时范围 100–10000 毫秒，覆盖响应头和响应体的完整读取。HTTP URL 不含查询参数、用户名或片段，主机必须在服务端 `RISK_VARIABLE_ALLOWED_HOSTS` 中（本地默认 `localhost,127.0.0.1`）。后台接口沿用项目统一 `ApiResponse<T>`；非法配置返回 HTTP 422 `INVALID_REQUEST`，版本冲突返回 HTTP 409 `VERSION_CONFLICT`，资源不存在返回 HTTP 404 `NOT_FOUND`。试取值的来源失败、超时或结果解析失败返回 HTTP 502 `VARIABLE_RESOLUTION_FAILED`，`data=null`；已移除的旧接口返回 HTTP 404。

### 营销样板模拟来源

`sourceType="DEMO_MARKETING"` 只用于当前营销闭环样板，由进程内模拟结算仓储提供只读数据。它不是可填任意 URL 的远程来源。三个固定定义分别为：

| 变量码 | `url` | `parameterNames` | `resultPath` | 含义 |
| --- | --- | --- | --- | --- |
| `demoOrderAmount` | `marketing://checkout` | `uid,checkoutId,experimentId` | `orderAmount` | 本次订单金额 |
| `demoMarginAfterMaxOffer` | `marketing://checkout` | `uid,checkoutId,experimentId` | `marginAfterMaxOffer` | 优惠前毛利减去固定最高档券额 18 元 |
| `demoRemainingBudget` | `marketing://budget` | `uid,checkoutId,experimentId` | `remainingBudget` | 固定总预算 2,000 元减去本次决策登记时的实验实际优惠额快照 |

这三项均为 `DECIMAL`，由演示初始化时创建首版，后续可在变量中心查看并由流程画布选择固定版本。后台创建 `DEMO_MARKETING` 定义时，服务端仅接受上表**三个固定变量码与其对应的 URL、字段和参数集合**；任意新业务码或将订单金额变量改指向毛利字段均返回 HTTP 422。模拟结算页面录入的订单金额和毛利先按 `uid + checkoutId + experimentId` 登记为样板事实，同时记录本次决策前已反馈的实际优惠额快照，再由变量中心按标识读取。同一 `uid` 的不同结算不会混用；事实不存在或 `uid` 不匹配时变量取值失败。订单、反馈和待取值事实均保存在当前应用上下文内，重启后清空。决策执行线程会临时绑定其所属样板仓储，避免 LiteFlow 全局节点在多 Spring 测试上下文中读取另一上下文的事实；执行结束立即解除绑定。变量定义及版本继续保存在数据库。

旧营销样板若仍使用仅声明 `experimentId` 的预算变量，初始化会在草稿未被人工修改的前提下创建符合新契约的变量版本，并重新发布资格规则与受影响流程。已有未发布草稿会保留并提示人工处理；不会自动删除本地数据库。

## 流程节点

`GET /api/components` 返回组件目录。唯一取数组件是 `get-variable`，其节点配置示例：

```json
{"id":"margin","kind":"COMPONENT","componentKey":"get-variable","failurePolicy":"FAIL_FAST","config":{"variableCode":"userMargin","versionId":12}}
```

变量中心根据固定依赖版本解析来源参数；请求输入变量码必须在该流程的 `requiredInputs` 中声明。发布时必须选变量**当前版本 ID**；已发布流程使用固定 ID，变量中心后续更新不会改变其行为。发布只校验配置与装载流程，不请求 HTTP 来源；运行时结果直接写入 `nodeResults.margin`，其值是一个强类型标量。

比较组件 `compare-value` 读取上游“获取变量”节点的单值：

```json
{"id":"checkMargin","kind":"CONDITION","componentKey":"compare-value","failurePolicy":"FAIL_FAST","config":{"sourceNodeId":"margin","valueType":"DECIMAL","operator":"GTE","expected":20}}
```

`STRING`、`BOOLEAN` 支持 `EQ`、`NE`；`DECIMAL` 还支持 `GT`、`GTE`、`LT`、`LTE`。发布时核对上游变量版本、值类型、预期值类型和节点可达性。取值失败时本次流程运行失败，不会落到条件的 `false` 分支。

流程上下文变量在变量中心保存全局名称、类型和固定版本；每条流程用顶层 `contextBindings` 指定唯一生产节点和结果路径。空路径读取整个标量结果，点分路径读取对象字段。值只在本次当前流程存在，子流程依靠显式输入映射。生产节点未执行、字段不存在或类型不符时，变量取值失败。

最小联调顺序：变量中心创建 → 试取值 → 流程中添加“获取变量”与“比较变量” → 校验、保存、发布 → 场景绑定 → 执行决策。旧 RPC 操作接口和 `http-json-get` 已在本次开发期重构中移除；本地验收使用文件型 H2 库重新初始化固定营销样板。

营销资格样板的联调顺序是：初始化三个请求输入变量、三个模拟来源变量和规则流程 → 模拟结算登记事实 → 外层流程仅传 `uid`、`checkoutId`、`experimentId` → 资格规则按固定变量版本读取三个强类型结果 → 产生资格结论。单独调用变量中心的试取值接口前须先有对应的模拟结算事实，否则返回来源失败。
