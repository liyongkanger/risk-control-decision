# 只读组件管理目录实施计划

**Goal:** 后台新增“组件管理”菜单，展示现有执行和条件组件的用途与契约，不提供启停或编辑。

**Architecture:** 复用代码注册的组件目录；扩充目录响应的职责和输出说明，后台按节点类型筛选并查看字段、默认配置。编辑器继续使用现有完整目录接口。

**Tech Stack:** Java 21、Spring Boot 4、React、Ant Design、Vitest。

**Spec:** `docs/design/component-management-boundary.md`

## Tasks

- [x] 给组件目录补充描述与输出契约，提供后台分页查询，先写 API 测试。
- [x] 写 `docs/api/component-catalog.md` 并核对响应。
- [x] 新增只读“组件管理”页面与菜单，验证搜索、类型筛选和详情。
- [x] 跑后端及前端测试、类型检查、构建和浏览器联调。
