# 变量中心直接重构设计

日期：2026-10-15。状态：实施基线。用户确认当前项目内建设变量中心、单变量单值强类型、取值失败使决策失败、流程固定变量版本、第一版只支持只读 HTTP 来源，并同意重建本地开发数据；备份不是前置条件。

## 目标与边界

后台新增“变量中心”菜单，管理 `STRING`、`BOOLEAN`、`DECIMAL` 类型变量。每个变量版本声明固定 HTTP GET 地址、查询参数名、JSON 结果字段路径、超时和结果类型。每次更新生成不可变版本，管理页面可按模拟输入测试取值。只读 HTTP 是变量中心内部来源类型，不作为流程组件暴露。

流程画布只提供一种“获取变量”取数节点。节点选择当前变量版本并映射流程输入；发布后固定版本 ID。执行时变量中心返回一个强类型标量，节点结果以节点 ID 保存。后续条件节点通过 `compare-value` 读取该标量，支持相等与 `DECIMAL` 的 `>`、`>=`、`<`、`<=`。缺失输入、HTTP 失败、结果路径缺失、空值或类型不符均使本次决策失败。

## 直接重构

移除 `http-json-get` 组件、RPC 操作 API、页面内登记表单及 Java RPC 操作模型；不提供旧流程兼容执行。保留已应用的 Flyway V5 文件，新增 V6 创建变量版本表并淘汰旧操作表。当前本地演示改用 `.local/variable-review` 文件型 H2 数据库；原 `.local/review.mv.db` 不删除。固定营销场景通过现有 `/api/demo/checkout-offer/setup` 重新初始化。

## 配置契约

- 变量：`variableCode`、`variableName`、`valueType`、`sourceType=HTTP_GET`、`url`、`timeoutMs`、`parameterNames`、`resultPath`、`versionNumber`、`id`。
- 获取变量节点：`componentKey=get-variable`，配置 `variableCode`、`versionId`、`inputMappings`；映射键覆盖变量全部查询参数，值引用流程声明的输入字段。
- 比较节点：`componentKey=compare-value`，配置 `sourceNodeId`、`operator`、`expected`。比较值的类型在发布时按上游变量版本校验；大小比较仅接受 `DECIMAL`。
- 发布阶段只校验结构、变量版本和输入映射，不发 HTTP 请求。运行时才取值；旧发布流程继续使用固定版本。

## 验收

后台登记和更新三类变量，测试 HTTP JSON 取值和类型转换；流程 `获取变量 → 比较变量 → RESULT` 能校验、保存、发布、执行。变量更新后已发布流程继续使用旧版本，新草稿须显式升级。验证超时、缺字段、类型不符和非法比较；完整营销流程与前后端测试通过。浏览器中能看到变量中心菜单与单一取数节点，无 RPC 操作菜单或旧节点。
