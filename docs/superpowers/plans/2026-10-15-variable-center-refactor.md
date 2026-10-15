# 变量中心直接重构实施计划

**Goal:** 将现有 RPC 操作及 HTTP 取数节点替换为项目内变量中心与单一“获取变量”节点。

**Architecture:** `component.variable` 保存不可变变量版本并执行受控只读 HTTP 查询；LiteFlow 的 `get-variable` 节点写入强类型标量；`compare-value` 条件节点判断标量。后台独立变量中心菜单管理变量，流程画布只配置变量引用和输入映射。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus、Flyway、LiteFlow、React、Ant Design、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-15-variable-center-refactor-design.md`

## Tasks

- [x] 重构数据表和变量 Service/API，覆盖分页、版本、类型及试取值测试。
- [x] 实现 `get-variable` 与 `compare-value`，覆盖执行、发布固定版本和失败语义。
- [x] 移除旧 RPC 操作模型、组件和接口；重建本地演示数据并验证固定营销流程。
- [x] 接入变量中心页面与获取变量节点配置，更新前后端契约文档。
- [x] 全量测试、类型检查、构建、浏览器联调及差异审查。
