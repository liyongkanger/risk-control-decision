# 风控决策后台首版实施计划

> 当前会话按本计划逐项实施和验证。用户要求合并提交，因此本计划不创建 Git 提交。

**Goal:** 用 Ant Design Pro v6 承载场景管理和 X6 流程设计，并让真实场景数据、草稿、发布及试运行形成后台闭环。

**Architecture:** `policy` 增加场景基本信息用例和接口，沿用 `common.response.ApiResponse<T>`；`web` 从 Vite 单页迁到 Umi Max 路由和 ProLayout，X6 保留为场景下的页面。登录与权限仅模拟前端展示。

**Tech Stack:** Java 21、Spring Boot 4.1、MyBatis-Plus、Flyway、MySQL 8、React 19、Ant Design Pro v6、Ant Design 6、Umi Max 4、AntV X6。

**Spec:** `docs/superpowers/specs/2026-10-13-admin-console-design.md`

## 全局约束

- 保留用户当前未提交的文档、`application.yml`、`ComponentRegistry.java` 及 V2 迁移改动；不自动提交。
- Java 使用 `@Resource`、中文类注释、普通类及 Lombok 默认写法；SQL 表和公共字段遵循 `docs/standards/`。
- 真实场景数据走后端；只模拟前端用户和静态菜单。
- 现有 `GraphDefinition`、`ApiResponse<T>`、草稿、校验、发布、执行语义继续使用。

## 文件职责

| 单元 | 文件与职责 |
| --- | --- |
| 数据迁移 | `src/main/resources/db/migration/V3__scene_metadata.sql` 为 `tb_scene` 增加 `scene_name`、`description` 并回填历史名称 |
| 场景业务 | `policy/mapper/SceneMapper.java` 增加场景查询与写入；`policy/service/SceneService.java`、`impl/SceneServiceImpl.java` 处理创建、编辑、查询；`policy/controller/SceneController.java` 暴露接口 |
| 接口对象 | `policy/vo/req`、`policy/vo/resp` 放新建、编辑、列表及场景详情对象 |
| 前端基座 | `web/package.json`、`web/config/config.ts`、`web/config/routes.ts`、`web/src/app.tsx` 定义 Umi Max 项目、静态菜单和模拟用户 |
| 场景页面 | `web/src/pages/scenes` 负责列表、新建、编辑、场景选择；统一从 `web/src/services/scene.ts` 调用真实接口 |
| 流程页面 | `web/src/pages/flow` 承载原 `web/src/flow` X6 编辑器，处理新场景初始图、示例模板和离页提示 |

## Task 1：场景表与业务接口

**依赖：** V2 规范表迁移作为 V3 的前置版本。

- [x] 先写迁移测试：V2 已有场景经过 V3 后 `scene_name=scene_code`，新场景的名称必填，场景码唯一。
- [x] 运行该测试确认红灯；新增 V3，使用向前迁移，不改 V1/V2。
- [x] 为 `SceneMapper` 增加 `findByCode`、`search`、`count`、`insertScene`、`updateScene`；正常读取筛选 `isactive=1`。
- [x] 先写 HTTP 测试覆盖新建、分页搜索、编辑、重复场景码、不可修改场景码、当前发布版本展示；再实现 `SceneService` 与 `SceneController`。
- [x] 草稿、校验和发布用例验证场景存在；删除发布时的隐式 `insertIfMissing`。更新此前直接按场景码发布的测试，让测试先创建场景。
- [x] 运行后端全套测试，验证 V1→V2→V3 与原有发布、决策执行。

接口约定：`GET /api/scenes?keyword=&page=1&pageSize=20` 返回 `ApiResponse<PageSceneResp>`；`POST /api/scenes` 接收 `CreateSceneReq(sceneCode, sceneName, description)`；`PUT /api/scenes/{sceneCode}` 接收 `UpdateSceneReq(sceneName, description)`；`GET /api/scenes/{sceneCode}` 返回 `ApiResponse<SceneResp>`。列表记录包含场景码、名称、描述、当前版本号、更新时间。

## Task 2：迁入 Ant Design Pro v6 后台基座

- [x] 参照官方 v6 最简项目，替换 Vite 单页启动配置为 Umi Max 4；保留 React 19、X6 与 Vitest 测试能力，安装依赖并验证锁文件。
- [x] 配置路由：`/` 跳转 `/scenes`，`/scenes` 和 `/flow-design` 显示菜单，`/scenes/:sceneCode/flow` 为隐藏详情路由。
- [x] 配置 ProLayout 侧栏、顶栏、面包屑、模拟用户；关闭登录跳转和动态权限依赖。
- [x] 在浏览器中冒烟验证路由与菜单，运行构建，确认后台壳可显示。

页面导航约定：流程设计菜单先进入场景选择；场景列表中的“设计流程”进入同一画布详情页。首版只有两个可用菜单。

## Task 3：场景管理页面

- [x] 先为场景 API 解包与错误映射编写测试，再实现 `web/src/services/scene.ts`。
- [x] 实现场景列表的加载、关键词搜索和分页；添加新建与编辑表单，场景码编辑时只读。
- [x] 从列表进入流程设计；`/flow-design` 使用同一场景列表数据做选择。
- [x] 验证场景创建、重复场景码提示、修改名称描述和返回列表后展示更新结果。

## Task 4：X6 流程设计页迁移

- [x] 将现有图转换与 API 测试迁到 Umi 项目并保持通过。
- [x] 新场景无草稿时显示仅含 `START`、`END` 的未保存图；演示流程由显式模板操作载入。
- [x] 场景页面展示名称和场景码，加载已有草稿；不存在场景显示错误页。
- [x] 处理未保存修改的路由离开与浏览器关闭提示；保存、校验、发布状态遵循现有修订号和快照约束。
- [x] 后台侧栏和窗口尺寸变化时更新 X6 画布尺寸；编辑器 CSS 限定到页面作用域。
- [x] 保留发布与试运行操作，验证节点问题定位和接口错误展示。

## Task 5：集成验证与文档

- [x] 运行 Java 21 下 `mvn test`；运行前端 `npm test`、`npm run build`。
- [x] 用浏览器验证后台导航、场景 CRUD、默认画布、模板、草稿、校验、发布和试运行。
- [x] 更新 README 的后台启动命令、菜单和接口说明；在 README 明确记录真实 MySQL 尚未联调，H2 结果不冒充 MySQL 验证。
- [x] 检查 `git diff --check` 与最终变更范围；保留工作区改动供用户统一提交。
