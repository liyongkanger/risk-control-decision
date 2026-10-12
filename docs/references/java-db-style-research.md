# Java 与数据库规范参考调研

日期：2026-10-12
状态：调研建议，尚未纳入强制规范。

当前已确认规则见 [项目开发规范](../standards/project-conventions.md)。本页记录官方文档和开源项目中可借鉴的写法，以及它们对本项目的具体影响。

## 1. Javadoc：先写摘要，再写约束和引用

[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) 建议 Javadoc 以简短摘要开头，多段说明之间留空行，`@param`、`@return`、`@throws` 按固定顺序排列；对语义显然的访问方法不要求重复注释。[JDK 21 Javadoc 规范](https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html) 明确支持 `{@link 类型#方法}` 和 `@see` 引用。

**建议采用**：所有类、接口、枚举写中文职责摘要；公共服务方法和复杂方法写约束、失败行为及必要链接。简单 getter/setter 不补充重复的 Javadoc。示例中 `{@link FailurePolicy#FAIL_FAST}` 比仅写“见失败策略枚举”更易定位。

Google 风格默认使用两空格缩进，与当前项目代码的四空格写法不同；只借鉴其 Javadoc 组织方式，不整体照搬格式。

## 2. 框架包：公开 service，限制内部依赖

[Spring Modulith 的模块约定](https://docs.spring.io/spring-modulith/reference/1.4/fundamentals.html) 区分对其他模块公开的接口与模块内部实现，还能将特定子包声明为 named interface。[ArchUnit](https://www.archunit.org/userguide/html/000_Index.html) 可通过测试表达“哪些包可以访问哪些包”的规则。

**建议采用的轻量做法**：保留已确认的 `component.liteflow.service` 对外边界，`registry`、`context`、`node` 和 `config` 作为内部包。等目录迁移完成后，用一条 ArchUnit 测试阻止其他模块直接依赖 LiteFlow 内部包。当前不必为了单条边界引入整个 Spring Modulith 运行体系。

## 3. 格式化：工具负责排版，规范负责语义

[Spotless Maven 插件](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md) 支持 Eclipse JDT 或 IntelliJ IDEA 的自定义格式设置，也能检查尾随空格和导入顺序。[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) 使用两空格缩进，因此直接采用 google-java-format 会改变当前项目的四空格风格。

**建议采用**：先完成 Java 类与包迁移，固定四空格排版；再加入与项目格式一致的 Spotless `check`。格式化工具不能代替“类职责清楚、注释写语义”这类人工评审。

## 4. MySQL：显式字符集、注释与业务码比较规则

[MySQL CREATE TABLE 文档](https://dev.mysql.com/doc/refman/8.4/en/create-table.html) 支持表和列的 `COMMENT`，也允许显式设置 `CHARSET`、`COLLATE`。[MySQL 排序规则说明](https://dev.mysql.com/doc/refman/8.4/en/charset-collation-names.html) 指出 `_ci` 表示不区分大小写。若 `scene_code` 使用此类排序规则，`payment` 与 `Payment` 在唯一约束下可能被视为同一个值；这是从文档规则对本项目的推断。

**建议新增一条项目决定**：场景码等机器标识统一归一化为小写，或为标识列使用明确的大小写敏感排序规则。两种做法择一，并让 Java 校验与数据库唯一约束一致。`isactive` 参与唯一约束时也需要单独设计：简单的 `UNIQUE(scene_code, isactive)` 只允许同一场景码有一条已删除记录；若业务码永久不复用，则保持 `UNIQUE(scene_code)` 更清晰。MySQL [UNIQUE 对 NULL 的处理](https://dev.mysql.com/doc/refman/8.0/en/create-index.html) 和[生成列索引](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html)提供了复杂场景的实现手段，当前暂不需要引入。

## 5. Flyway：已应用迁移保持不变

[Flyway 迁移文档](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)说明版本迁移及其校验和会记录在 schema history 中；[推荐实践](https://documentation.red-gate.com/fd/recommended-practices-150700352.html)建议用新的版本迁移表达后续变更。

**对当前项目的影响**：`V1__scene_flow.sql` 已在仓库中使用，但尚未确认是否应用过任何真实 MySQL 环境。调整现有三张表前，应先检查目标环境的 `flyway_schema_history`；如果已应用，新增迁移；如果只在一次性本地测试库执行过，仍应明确处理方式，避免已存在环境和全新环境得到不同结构。
