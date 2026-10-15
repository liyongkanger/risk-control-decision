# 变量中心与决策取值接口

状态：已实现的第一版。变量中心在当前应用内运行；来源仅支持受控的只读 HTTP GET，输出一个 `STRING`、`BOOLEAN` 或 `DECIMAL` 值。数据库查询来源、写操作、认证、重试与默认值不在本版范围内。

## 变量定义与版本

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/variables?keyword=&page=1&pageSize=10` | 按变量码或名称分页查询当前版本 |
| `GET` | `/api/variables/{code}` | 获取当前版本 |
| `GET` | `/api/variables/versions/{id}` | 获取指定不可变版本 |
| `POST` | `/api/variables` | 创建变量首版 |
| `PUT` | `/api/variables/{code}` | 基于预期版本号创建新版 |
| `POST` | `/api/variables/{code}/test` | 使用当前版本和模拟输入试取值 |

创建请求：

```json
{"variableCode":"userMargin","variableName":"用户订单毛利","valueType":"DECIMAL","sourceType":"HTTP_GET","url":"http://localhost:9199/margin","timeoutMs":1000,"parameterNames":["userId"],"resultPath":"data.margin"}
```

更新请求保留除 `variableCode` 外的字段，并增加 `expectedVersion`。更新产生新版本 ID 和版本号；旧版本不变。成功响应 `data` 为 `{id,variableCode,variableName,versionNumber,valueType,sourceType,url,timeoutMs,parameterNames,resultPath}`。分页响应的 `data` 为 `{records,total,page,pageSize}`；`page>=1`，`1<=pageSize<=100`。

试取值请求：`{"input":{"userId":"user123"}}`。成功 `data` 为 `{variableCode,versionId,valueType,value}`，其中 `value` 是一个与 `valueType` 对应的 JSON 标量。HTTP 响应必须为 JSON 对象，`resultPath` 指向其中一个非空字段；原始响应对象不暴露给决策流程。缺字段、类型不符、超时、非 2xx 和过大响应均使取值失败。

变量码为以字母开头的 1–64 位字母、数字、下划线或连字符；参数名最多 20 个且不可重复。超时范围 100–10000 毫秒，覆盖响应头和响应体的完整读取。HTTP URL 不含查询参数、用户名或片段，主机必须在服务端 `RISK_VARIABLE_ALLOWED_HOSTS` 中（本地默认 `localhost,127.0.0.1`）。后台接口沿用项目统一 `ApiResponse<T>`；非法配置返回 HTTP 422 `INVALID_REQUEST`，版本冲突返回 HTTP 409 `VERSION_CONFLICT`，资源不存在返回 HTTP 404 `NOT_FOUND`。试取值的来源失败、超时或结果解析失败返回 HTTP 502 `VARIABLE_RESOLUTION_FAILED`，`data=null`；已移除的旧接口返回 HTTP 404。

## 流程节点

`GET /api/components` 返回组件目录。唯一取数组件是 `get-variable`，其节点配置示例：

```json
{"id":"margin","kind":"COMPONENT","componentKey":"get-variable","failurePolicy":"FAIL_FAST","config":{"variableCode":"userMargin","versionId":12,"inputMappings":{"userId":"userId"}}}
```

映射键须覆盖变量版本的全部 `parameterNames`，映射值须在该流程的 `requiredInputs` 中声明。发布时必须选变量**当前版本 ID**；已发布流程使用固定 ID，变量中心后续更新不会改变其行为。发布只校验配置与装载流程，不请求 HTTP 来源；运行时结果直接写入 `nodeResults.margin`，其值是一个强类型标量。

比较组件 `compare-value` 读取上游“获取变量”节点的单值：

```json
{"id":"checkMargin","kind":"CONDITION","componentKey":"compare-value","failurePolicy":"FAIL_FAST","config":{"sourceNodeId":"margin","valueType":"DECIMAL","operator":"GTE","expected":20}}
```

`STRING`、`BOOLEAN` 支持 `EQ`、`NE`；`DECIMAL` 还支持 `GT`、`GTE`、`LT`、`LTE`。发布时核对上游变量版本、值类型、预期值类型和节点可达性。取值失败时本次流程运行失败，不会落到条件的 `false` 分支。

最小联调顺序：变量中心创建 → 试取值 → 流程中添加“获取变量”与“比较变量” → 校验、保存、发布 → 场景绑定 → 执行决策。旧 RPC 操作接口和 `http-json-get` 已在本次开发期重构中移除；本地验收使用新的文件型 H2 库重新初始化固定营销样板。
