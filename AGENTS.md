# 项目开发约定

本仓库的代码、组件分包和数据库设计统一遵循 [项目规范](docs/standards/project-conventions.md)。

- Java 21 运行，代码采用偏 Java 8 的显式写法；不使用 `record`、`var`、模式匹配简写，新式 `switch` 与 Java 21 API 可用。
- 类、接口、枚举写中文 Javadoc；复杂方法说明关键逻辑，并在适用时用 `{@link ...}` 引用相关类型或方法。
- 框架能力放在 `component.<框架名>` 下；通过该框架包的 `service` 向其他模块提供方法，内部再按职责划分 `node`、`registry`、`context`、`config` 等包。
- 新建业务表以 `tb_` 开头，包含 `id`、`inserttime`、`updatetime`、`isactive`，并遵守规范中的命名、注释和逻辑删除约定。

现有代码和迁移脚本尚未全部符合该规范；修改相关文件时应逐步收敛，不要把当前写法当作新代码模板。
