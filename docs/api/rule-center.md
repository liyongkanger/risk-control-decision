# 规则中心接口

状态：第一版已实现；规则中心页面、流程组件及营销资格样板均已接入。

所有接口使用 `ApiResponse<T>`：成功为 HTTP 200、`code=SUCCESS`、`data` 为下表对象；失败时 `data=null`。规则集在当前应用内保存，规则执行只读取本次输入，不访问外部数据源或修改业务状态。

| 方法 | 路径 | 请求 | 成功 `data` |
| --- | --- | --- | --- |
| `GET` | `/api/rule-sets?keyword=&page=1&pageSize=10` | `page>=1`，`1<=pageSize<=100`；关键词匹配规则集码或名称 | `{records,total,page,pageSize}`；空页 `records=[]`；每行只含 `code,name,draftRevision,currentVersionId` |
| `POST` | `/api/rule-sets` | `{code,name,definition}` | 规则集草稿 |
| `GET` | `/api/rule-sets/{code}` | 规则集码 | 规则集草稿 |
| `PUT` | `/api/rule-sets/{code}/draft` | `{expectedRevision,definition}` | 新草稿及新修订号 |
| `POST` | `/api/rule-sets/{code}/test` | `{input:{字段名:强类型值}}` | 试算结果；`ruleVersionId=0` 表示草稿 |
| `POST` | `/api/rule-sets/{code}/versions/{id}/test` | `{input:{字段名:强类型值}}` | 指定不可变版本的试算结果；版本须属于该规则集 |
| `POST` | `/api/rule-sets/{code}/publish` | `{expectedRevision}` | 不可变发布版本 |
| `GET` | `/api/rule-sets/versions/{id}` | 正整数版本 ID | 指定不可变版本 |

规则集草稿响应为 `{code,name,draftRevision,definition,currentVersionId}`；从未发布时 `currentVersionId=null`。版本响应为 `{id,code,versionNumber,definition}`；同一规则集从版本号 1 开始递增。更新草稿不改变已有版本。只有草稿修订号匹配且该修订尚未发布时才能发布，重复发布相同修订返回冲突。流程节点引用 `id`，不会随规则集新版本改变。

规则定义示例：

```json
{
  "inputs":[{"name":"orderAmount","type":"DECIMAL"}],
  "resultCodes":["ELIGIBLE","INELIGIBLE"],
  "rows":[{"id":"min-order","priority":10,
    "conditions":[{"input":"orderAmount","operator":"LT","expected":100}],
    "result":{"resultCode":"INELIGIBLE","reasonCode":"ORDER_LOW","reason":"订单金额不足"}}],
  "defaultResult":{"resultCode":"ELIGIBLE","reasonCode":"QUALIFIED","reason":"符合资格"}
}
```

输入类型支持 `STRING`、`BOOLEAN`、`DECIMAL`。三种类型可用 `EQ`、`NE`，仅 `DECIMAL` 可用 `GT`、`GTE`、`LT`、`LTE`。`DECIMAL` 的比较常量和试算输入可用 JSON 数字或十进制字符串；服务端将小数直接解析为 `BigDecimal`，返回给浏览器的比较常量统一为十进制字符串。后台表单使用文本维护小数，试算输入的小数也应写成字符串，例如 `"0.10000000000000001"`，避免浏览器先做浮点舍入。同一行条件按 `AND` 判断；按 `priority` 升序匹配第一行，未命中时返回必填的 `defaultResult`。行 ID、优先级、输入名和结果码不得重复。规则集最多 20 个输入、200 行，每行最多 10 个条件。规则集码及字段码以字母开头，最长 64 位，只含字母、数字、下划线和连字符；名称最长 128 位。

试算响应示例：

```json
{"code":"SUCCESS","message":"成功","data":{"resultCode":"INELIGIBLE","reasonCode":"ORDER_LOW","reason":"订单金额不足","matchedRuleId":"min-order","ruleSetCode":"checkoutEligibility","ruleVersionId":0}}
```

兜底命中时 `matchedRuleId=null`；缺字段或类型不符是 HTTP 422 `INVALID_REQUEST`，不返回兜底结果。规则定义或分页参数无效也是 HTTP 422 `INVALID_REQUEST`；草稿修订号、重复规则集码或重复发布冲突为 HTTP 409 `VERSION_CONFLICT`；规则集或版本不存在为 HTTP 404 `NOT_FOUND`；请求 JSON 无法解析为 HTTP 400 `INVALID_REQUEST`。所有这些失败响应的 `data` 为 `null`。

最小调用顺序：创建规则集 → 用模拟输入试算草稿 → 发布 → 在流程画布选择当前版本 → 保存并发布流程 → 执行决策。规则集新版发布后，旧流程继续引用旧版；可用固定版本试算接口复现历史结果。草稿流程必须重新选择新版才能发布。
