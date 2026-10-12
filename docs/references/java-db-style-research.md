# 项目新增规范候选调研

日期：2026-10-12
状态：调研建议，尚未纳入 [项目开发规范](../standards/project-conventions.md)。

本文专门筛选**已确认规则之外**值得加入的规范。排序依据是它们对当前风控决策平台的正确性、可维护性和协作成本的影响。

## 优先级一：现在值得补充的规则

### 1. 明确 Controller、Service、Mapper 的交付边界

**候选规则**：Controller 只负责请求解析、校验与响应映射；对外使用独立的请求/响应类，不直接返回数据库对象或 LiteFlow 内部对象。Service 承担业务用例和事务边界；Mapper 只负责 SQL 与数据库映射。必需依赖通过构造方法注入，保持字段 `final`。

**当前代码中的例子**：`FlowAdminController.publish()` 直接返回 `FlowVersion`，会把图快照和编译后的 EL 一并暴露给前端。更清楚的响应应只包含调用方需要的版本 ID、版本号与 chain ID。Spring 官方推荐构造方法注入必需依赖，以保证对象初始化完整。[Spring 依赖注入文档](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)

### 2. 输入在服务端校验，错误响应保持一种契约

**候选规则**：外部请求先校验大小、格式和业务语义；浏览器校验只用于即时提示。服务端错误统一包含稳定错误码、可读说明和必要定位信息，不向客户端返回内部异常或 SQL。可继续使用项目自定义错误对象，也可迁移到 Spring 的 `ProblemDetail`，但同一版本 API 不混用两种格式。

**当前代码中的例子**：图校验错误包含节点 ID 是好的基础；场景码格式、请求体大小和版本冲突也应按统一契约返回。OWASP 明确要求即使浏览器已经校验，服务端仍要校验；Spring MVC 原生支持 RFC 9457 的 `ProblemDetail`。[OWASP 输入校验](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html)、[Spring 错误响应](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)

### 3. MyBatis 参数绑定与查询约定

**候选规则**：用户或流程输入作为 SQL 值时统一使用 `#{}` 参数绑定，不把输入拼入 SQL，也不用 `${}` 代入值；确需动态表名或列名时，只能从代码维护的白名单选择。查询明确选择所需列，表字段变化时检查 Mapper 映射。所有逻辑删除表的正常读取路径显式筛选 `isactive = 1`。

**原因**：MyBatis 官方说明 `#{}` 使用 `PreparedStatement` 参数占位，而 `${}` 是直接文本替换；前者适合数据值，后者必须严格限制来源。[MyBatis XML 映射文档](https://mybatis.org/mybatis-3/zh_CN/sqlmap-xml.html)

### 4. 事务与外部执行分开

**候选规则**：数据库事务由 Service 的公开用例方法定义；事务中完成需要原子化的数据库写入，不把远程调用或完整 LiteFlow 执行放在长事务内。跨数据库与内存链注册的流程明确状态与补偿方式，不假定 `@Transactional` 能回滚内存或外部副作用。需要受检异常触发回滚时显式配置回滚规则。

**当前代码中的例子**：流程发布同时涉及 MySQL 版本记录和 LiteFlow 内存链，已经采用“写入未发布版本 → 加载链 → 条件切换指针”的步骤；后续应把状态与失败重试语义写成明确契约。Spring 默认只因运行时异常回滚，受检异常需要配置。[Spring 回滚规则](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)

### 5. 日志和轨迹遵循最小信息原则

**候选规则**：日志与轨迹记录 `requestId`、`sceneCode`、版本、节点 ID、耗时、结果状态和错误码；请求原文、令牌、密码、数据库连接串和敏感用户数据不得直接记录。可识别用户的信息按业务需要脱敏或取摘要。

**当前代码中的例子**：`NodeTrace` 已有流程定位字段，后续新增远程调用、查库组件时尤其需要避免把输入和响应整体打印。OWASP 对敏感数据和日志注入有明确建议。[OWASP 日志规范](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)

## 优先级二：迁移代码时同步引入

### 6. 用工具保证排版，用测试保证包边界

**候选规则**：每个 Java 文件最多一个顶层公开类型；禁止通配符 import；覆写方法使用 `@Override`；捕获异常不能空处理。这些是可检查的代码卫生规则。采用四空格格式配置的 Spotless `check` 保持排版一致；用 ArchUnit 测试约束其他模块只依赖 `component.<框架名>.service`，不依赖其 `node`、`registry`、`context` 实现。

Google Java 风格给出了单文件单顶层类、import、`@Override` 和异常处理的明确写法；但其缩进为两空格，不能直接把整套格式强加给当前项目。Spotless 支持自定义 Eclipse/IDEA 格式；ArchUnit 可验证包依赖。[Google Java 风格](https://google.github.io/styleguide/javaguide.html)、[Spotless Maven](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md)、[ArchUnit 指南](https://www.archunit.org/userguide/html/000_Index.html)

### 7. 表结构按查询和生命周期设计

**候选规则**：新字段优先明确 `NOT NULL` 及默认值，例外要说明；索引依据真实查询条件设计，新查询上线前用 `EXPLAIN` 核验，不机械地给每个公共字段建索引。字符串业务码必须确定大小写规则：归一化为小写，或使用明确的大小写敏感排序规则。业务码与 `isactive` 的唯一性关系也要在建表时定清楚。

**当前代码中的例子**：`scene_code` 若使用 `_ci` 排序规则，`payment` 和 `Payment` 会按不区分大小写比较。这是根据 MySQL 排序规则对本项目的推断。MySQL 提供 `EXPLAIN` 查看执行计划；严格模式下，遗漏无默认值的 `NOT NULL` 字段会报错。[MySQL 排序规则](https://dev.mysql.com/doc/refman/8.4/en/charset-collation-names.html)、[EXPLAIN](https://dev.mysql.com/doc/refman/8.4/en/explain.html)、[字段默认值](https://dev.mysql.com/doc/refman/8.4/en/data-type-defaults.html)

### 8. 数据库迁移只向前演进

**候选规则**：Flyway 版本迁移一旦在共享环境应用，后续结构变化新增版本脚本；部署前校验历史状态与目标库版本。表和字段注释随迁移一起维护。当前 `V1__scene_flow.sql` 是否已在真实 MySQL 环境应用尚未确认，不能直接假定可重写。

[Flyway 迁移说明](https://documentation.red-gate.com/flyway/flyway-concepts/migrations) 和[推荐实践](https://documentation.red-gate.com/fd/recommended-practices-150700352.html)支持这一做法。

## 暂不建议加入的规则

- **强制 Lombok**：当前项目没有统一使用它；引入后还需重新确定 DTO、实体和领域对象的生成方法范围。
- **照搬 Google 两空格格式**：当前代码与项目规范采用四空格，更换全部排版会制造大量无业务意义的差异。
- **一开始引入 Spring Modulith 全套功能**：目前只有单模块项目和少量包边界，用 ArchUnit 测试即可先约束依赖；以后模块规模扩大再评估。
- **固定单测覆盖率数字**：当前更需要针对图转换、发布并发、版本固定和框架适配编写有意义的行为测试。覆盖率阈值应在测试基线建立后决定。
