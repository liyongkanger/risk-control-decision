# 组件能力目录接口

状态：已实现。目录由服务端代码注册，后台“组件管理”只读展示；本接口不创建、编辑、启用或停用组件。变量定义由[变量中心](variable-center.md)管理，开始、结束、汇合、业务结果和子流程调用属于平台结构节点，不在本目录中。

## 获取完整目录

`GET /api/components` 返回 `ApiResponse<ComponentDefinitionResp[]>`，供流程编辑器加载可选能力。当前有 7 项，按 `key` 升序。空目录时 `data=[]`，不会返回 `null`。

单项字段：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `key` | string | 稳定组件标识，用于节点 `componentKey` |
| `displayName` | string | 后台展示名 |
| `description` | string | 能力用途及边界 |
| `condition` | boolean | `true` 适用于 `CONDITION`，`false` 适用于 `COMPONENT` |
| `category` | `DATA` / `CONDITION` / `RULE` | 组件管理二级菜单分类，与画布节点类型独立 |
| `outputType` | `ANY` / `BOOLEAN` / `VARIABLE_VALUE` / `RULE_RESULT` | 节点结果契约；`VARIABLE_VALUE` 的实际类型由选中的变量版本确定 |
| `configFields` | array | 字段 `name`、`label`、`type`（`STRING`、`NUMBER`、`OBJECT` 或 `ANY`）、`required` |
| `defaultConfig` | object | 编辑器创建节点时的初始配置，尚不代表可发布 |

示例节选：

```json
{"code":"SUCCESS","message":"成功","data":[{"key":"get-variable","displayName":"获取变量","description":"引用变量中心的固定版本，按变量依赖取值并输出一个强类型值。","condition":false,"category":"DATA","outputType":"VARIABLE_VALUE","configFields":[{"name":"variableCode","label":"变量码","type":"STRING","required":true}],"defaultConfig":{"variableCode":"","versionId":0}}]}
```

示例只展示一项和一个配置字段；实际接口会返回完整的 7 项和每项全部字段。规则能力包括 `execute-rule-set`（普通组件，输出 `RULE_RESULT`）和 `rule-result-equals`（条件组件，输出 `BOOLEAN`）；具体规则集在[规则中心](rule-center.md)维护。

## 后台分页查询

`GET /api/components/page?keyword=&nodeKind=&category=&page=1&pageSize=10` 返回统一响应壳中的 `{records,total,page,pageSize}`。`keyword` 匹配组件标识或展示名，不区分大小写；`nodeKind` 可为空、`COMPONENT` 或 `CONDITION`；`category` 可为空、`DATA`、`CONDITION` 或 `RULE`，两种筛选可以组合。`page` 从 1 开始，`pageSize` 为 1–100；非法值返回 HTTP 422 `INVALID_REQUEST`，`data=null`。没有匹配项时 `records=[]`、`total=0`。

目录目前由代码注册，分页在服务端对固定目录完成，不读取数据库。响应按 `key` 升序；前端只渲染当前页，不自行切片。组件的默认配置仍须经过对应节点的发布校验；目录展示并不意味着任意参数组合都可执行。
