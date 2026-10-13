# 项目开发约定

本仓库的代码风格、注释、组件分包和数据库表结构规范统一以 [项目开发规范](docs/standards/project-conventions.md) 为准。编写或修改相关代码前，先阅读该文档。

Controller、Service、Mapper、DTO/VO、Assembler、远程调用、异常和日志的分层写法见 [分层开发规范](docs/standards/mvc-layer-conventions.md)。

场景管理及其他后台列表查询遵循 [后台列表分页约定](docs/standards/admin-list-pagination.md)。

前后端功能按 [前后端协作与接口交接规范](docs/standards/frontend-backend-collaboration.md) 分阶段推进；后端接口供前端接入前，应提供并核对 `docs/api/` 下对应的接口文档。

创建 Git 提交时遵循 [Git 提交规范](docs/standards/commit-conventions.md)，并先核对暂存区只包含本次改动。

规范调研文档仅供讨论；未经确认的候选规则不作为本仓库的开发要求。
