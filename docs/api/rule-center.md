# 规则中心接口

状态：第一版已实现；规则中心页面、流程组件及营销资格样板均已接入。

所有接口使用 `ApiResponse<T>`：成功为 HTTP 200、`code=SUCCESS`、`data` 为下表对象；失败时 `data=null`。规则集在当前应用内保存；绑定变量的规则输入在试算或执行时通过变量中心按固定版本取值。

| 方法 | 路径 | 请求 | 成功 `data` |
| --- | --- | --- | --- |
| `GET` | `/api/rule-sets?keyword=&page=1&pageSize=10` | `page>=1`，`1<=pageSize<=100`；关键词匹配规则集码或名称 | `{records,total,page,pageSize}`；空页 `records=[]`；每行只含 `code,name,draftRevision,currentVersionId` |
| `POST` | `/api/rule-sets` | `{code,name,definition}` | 规则集草稿 |
| `GET` | `/api/rule-sets/{code}` | 规则集码 | 规则集草稿 |
| `PUT` | `/api/rule-sets/{code}/draft` | `{expectedRevision,definition}` | 新草稿及新修订号 |
| `POST` | `/api/rule-sets/{code}/test` | `{input:{请求变量码:值},contextValues:{流程上下文变量码:临时值}}` | 试算结果；`ruleVersionId=0` 表示草稿 |
| `POST` | `/api/rule-sets/{code}/versions/{id}/test` | 与草稿试算相同 | 指定不可变版本的试算结果；版本须属于该规则集 |
| `POST` | `/api/rule-sets/{code}/publish` | `{expectedRevision}` | 不可变发布版本 |
| `GET` | `/api/rule-sets/versions/{id}` | 正整数版本 ID | 指定不可变版本 |

规则集草稿响应为 `{code,name,draftRevision,definition,currentVersionId}`；从未发布时 `currentVersionId=null`。版本响应为 `{id,code,versionNumber,definition}`；同一规则集从版本号 1 开始递增。更新草稿不改变已有版本。只有草稿修订号匹配且该修订尚未发布时才能发布，重复发布相同修订返回冲突。流程节点引用 `id`，不会随规则集新版本改变。

规则定义示例：

```json
{
  "inputs":[{"name":"orderAmount","type":"DECIMAL","variableBinding":{
    "variableCode":"demoOrderAmount","variableVersionId":12}}],
  "resultCodes":["ELIGIBLE","INELIGIBLE"],
  "rows":[{"id":"min-order","priority":10,
    "conditions":[{"input":"orderAmount","operator":"LT","expected":100}],
    "result":{"resultCode":"INELIGIBLE","reasonCode":"ORDER_LOW","reason":"订单金额不足"}}],
  "defaultResult":{"resultCode":"ELIGIBLE","reasonCode":"QUALIFIED","reason":"符合资格"}
}
```

输入类型支持 `STRING`、`BOOLEAN`、`DECIMAL`。三种类型可用 `EQ`、`NE`，仅 `DECIMAL` 可用 `GT`、`GTE`、`LT`、`LTE`。`DECIMAL` 的比较常量和试算输入可用 JSON 数字或十进制字符串；服务端将小数直接解析为 `BigDecimal`，返回给浏览器的比较常量统一为十进制字符串。后台表单使用文本维护小数，试算输入的小数也应写成字符串，例如 `"0.10000000000000001"`，避免浏览器先做浮点舍入。同一行条件按 `AND` 判断；按 `priority` 升序匹配第一行，未命中时返回必填的 `defaultResult`。行 ID、优先级、输入名和结果码不得重复。规则集最多 20 个输入、200 行，每行最多 10 个条件。规则集码及字段码以字母开头，最长 64 位，只含字母、数字、下划线和连字符；名称最长 128 位。

每个输入必须提供 `variableBinding`；`variableCode` 与 `variableVersionId` 指向变量中心同一不可变版本。规则保存时校验版本归属和类型，发布时要求所选版本是变量当前版。外部变量的参数及固定依赖版本在变量中心定义；同一次规则求值的多个输入共享解析缓存，相同固定版本的外部变量只取一次。规则节点只配置 `ruleSetCode` 和 `ruleVersionId`，不接受 `inputMappings`。如上例试算请求传 `{"input":{"uid":"u1","checkoutId":"c1","experimentId":"e1"},"contextValues":{}}`，实际取值仍取决于变量来源是否有对应数据。`contextValues` 仅用于规则单独试算，正式流程不会读取该样例；缺失流程上下文绑定时决策失败。本仓库尚未进入生产测试，旧的未绑定规则输入契约已移除。

规则中心页面新增规则时，先打开变量中心选择器，按变量码或名称搜索当前变量版本，也可按业务分类筛选；选择后自动建立固定版本绑定、填入值类型，并用变量码作为默认规则输入名。规则输入名可编辑，页面会同步更新该规则草稿中条件对输入名的引用，且拒绝与其他输入重名。更换已有输入的变量会保留输入名；若值类型变化，引用它的条件会重置为 `EQ` 并清空比较值，需重新填写。变量选择器使用后端分页结果，不以首批 100 个变量作为全集。试算框根据固定变量版本递归生成请求字段和流程上下文样例；外部来源仍真实取值，样例值需由操作者改为可用数据。

规则行编辑器按“规则标识与优先级 → AND 条件 → 命中结果”展示，新增行自动生成未使用的 `rule-N` 标识及当前最大优先级加 10。新增条件或切换到不同值类型时，比较值置空并要求显式填写；更换已绑定输入的值类型时，引用它的条件重置为 `EQ` 且比较值置空。页面将 `defaultResult` 标成“所有规则都未命中时”，明确它是正常业务结果，不处理变量取值或流程执行失败。页面布局不改变按优先级升序首命中的接口语义。

规则中心的试算区按「填写数据 → 选择试算对象 → 查看结果」展示。页面从规则绑定的固定变量版本及其依赖生成请求输入、流程上下文字段；高级编辑仍可直接补充 JSON，十进制数用字符串保留精度。试算已发布版本时，先读取该版本定义并载入对应输入项，历史版本不会沿用当前草稿的字段。结果区分别显示试算版本、命中的规则行或所有规则未命中、业务结果码、原因码和原因；变量取值失败等接口错误单独展示，不会当作未命中结果。

试算响应示例：

```json
{"code":"SUCCESS","message":"成功","data":{"resultCode":"INELIGIBLE","reasonCode":"ORDER_LOW","reason":"订单金额不足","matchedRuleId":"min-order","ruleSetCode":"checkoutEligibility","ruleVersionId":0}}
```

兜底命中时 `matchedRuleId=null`；变量缺值、类型不符或外部来源失败返回 HTTP 502 `VARIABLE_RESOLUTION_FAILED`，不返回兜底结果。规则定义或分页参数无效为 HTTP 422 `INVALID_REQUEST`；草稿修订号、重复规则集码或重复发布冲突为 HTTP 409 `VERSION_CONFLICT`；规则集或版本不存在为 HTTP 404 `NOT_FOUND`；请求 JSON 无法解析为 HTTP 400 `INVALID_REQUEST`。这些失败响应的 `data` 为 `null`。

最小调用顺序：创建规则集 → 用模拟输入试算草稿 → 发布 → 在流程画布选择当前版本 → 保存并发布流程 → 执行决策。规则集新版发布后，旧流程继续引用旧版；可用固定版本试算接口复现历史结果。草稿流程必须重新选择新版才能发布。
