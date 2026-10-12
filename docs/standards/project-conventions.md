# 风控决策平台开发规范

状态：已确认的项目约定
适用范围：`risk-control-decision` 仓库的后端代码、组件框架和业务表设计。后续项目可以参考本规范，但本文件只约束当前仓库。

## 1. Java 代码风格

### 1.1 语言与写法

- 使用 Java 21 编译和运行，可使用 Java 21 标准库 API。
- 保持偏 Java 8 的显式类和变量写法：不用 `record`、`var`、`instanceof` 模式变量等简写。新式 `switch` 可以使用。
- 数据对象使用普通类，清楚声明字段、构造方法和访问方法。是否使用 Lombok 按具体模块统一选择；本规范不强制引入 Lombok。
- 类名使用 `UpperCamelCase`，方法和变量使用 `lowerCamelCase`，常量使用 `UPPER_SNAKE_CASE`。包名全小写，按职责分包。
- 依赖注入方式及 Controller、Service、Mapper 的具体写法遵循[分层开发规范](mvc-layer-conventions.md)。公共逻辑集中在明确的服务或工具类中，避免散落复制。

### 1.2 注释与 Javadoc

- 类、接口和枚举必须有中文 Javadoc，说明职责与使用边界。
- 对外提供的服务方法、包含分支/并发/事务/转换规则的复杂方法应有中文 Javadoc。注释解释语义、约束和失败行为，避免复述代码。
- 枚举值应有中文含义说明。复杂方法涉及枚举、其他方法或关键类型时，在 Javadoc 中使用 `{@link 类型}`、`{@link 类型#方法}` 或 `@see` 建立可跳转引用。
- 文件采用 UTF-8 编码，Javadoc 排版保持短段落和完整句子；参数与返回值有非显然约束时写 `@param`、`@return`，可能抛出的业务异常写 `@throws`。

示例：

```java
/**
 * 根据场景码选择已发布的流程版本，并执行同步决策。
 *
 * <p>本次执行固定使用选中的版本；发布指针后续变化不会影响当前请求。
 * 流程执行失败时返回失败状态，节点失败策略见 {@link FailurePolicy}。</p>
 *
 * @param sceneCode 场景码，不得为空
 * @return 本次流程的执行结果
 * @see FlowVersion
 */
public ExecutionResult evaluate(String sceneCode) {
    // 示例仅展示注释格式。
    return null;
}
```

## 2. 组件框架分包

`component` 存放可复用的框架能力，不设置通用的 `component.api` 包。每接入一种框架或能力，建立 `component.<框架名>`；该框架通过自己的 `service` 包向 `policy`、`decision`、`orchestrator` 等模块提供方法。内部子包只在有实际职责时创建，不预先生成空包。

LiteFlow 的目标结构：

```text
component/
└── liteflow/
    ├── service/       对其他模块提供流程加载、执行等方法
    ├── registry/      chain、节点及实现的注册和查找
    ├── context/       单次执行上下文及节点结果
    ├── node/          LiteFlow 节点实现
    │   ├── http/      远程调用节点（需要时）
    │   ├── database/  查库节点（需要时）
    │   └── demo/      示例节点
    └── config/        LiteFlow 与 Spring 的配置
```

- `component.liteflow` 负责 LiteFlow 专属的执行适配与支撑能力；`orchestrator` 负责图结构校验和流程语义转换；`policy` 负责场景、草稿与版本发布。
- 流程节点是一次流程中对可复用组件的引用及其配置。组件实现不依赖特定场景，节点的配置和结果保存在本次执行上下文中。
- 其他模块通过 `component.liteflow.service` 调用能力，不直接操作 LiteFlow 的注册表、上下文实现或节点类。
- 后续引入其他框架能力时沿用“框架包 → `service` 对外方法 → 内部按职责分包”的方式；各框架内部结构可按实际能力调整。

## 3. MySQL 业务表规范

### 3.1 命名与公共字段

- 业务表名以 `tb_` 开头；表名、字段名使用小写字母和下划线，表达业务对象与用途。当前流程表目标名为 `tb_scene`、`tb_flow_draft`、`tb_flow_version`。
- 每张业务表包含 `id` 自增主键、`inserttime`、`updatetime`、`isactive`。`scene_code` 等业务标识设置唯一约束，不代替 `id` 主键。
- `inserttime` 为插入时间，插入时由数据库生成；`updatetime` 为更新时间，插入及更新时由数据库维护。
- `isactive` 为逻辑删除标记：`1` 表示有效，`0` 表示已删除，默认 `1`。常规业务查询明确筛选 `isactive = 1`；删除操作更新该字段，不物理删除业务记录。
- 已发布的不可变流程版本在切换当前版本后仍保留为有效历史记录，不因发布指针切换而设置 `isactive = 0`。

### 3.2 字段、索引与 DDL

- 表和业务字段写清楚的中文 `COMMENT`；公共字段也写中文说明。
- 主键统一为 `id BIGINT`，与 Java `Long` 对应。索引名使用 `uniq_`（唯一索引）和 `idx_`（普通索引）前缀；索引按实际查询与唯一性约束设计，不因公共字段存在就机械地为每个字段建索引。
- 金额、比率等需要精确计算的数值使用 `DECIMAL`，不使用 `FLOAT`、`DOUBLE` 存储精确业务值。
- 新表使用 InnoDB、`utf8mb4` 字符集；排序规则在项目数据库环境确定后保持统一。迁移脚本由 Flyway 管理，已应用的迁移不原地改写。

参考模板：

```sql
CREATE TABLE `tb_example` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scene_code` VARCHAR(64) NOT NULL COMMENT '场景码',
    `inserttime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
    `updatetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isactive` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '逻辑删除标记：1有效，0删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_scene_code` (`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例业务表';
```

Flyway 自身创建的元数据表不是业务表，不受 `tb_` 和业务公共字段约束。

## 4. 与当前代码的关系

本文件先记录已确认规则，不表示现有实现已完成迁移。目前部分 Java 文件使用 `record`、`var` 和模式匹配，`component` 内容仍平铺，`V1__scene_flow.sql` 的表名与公共字段也不符合本规范。迁移现有代码和数据库结构应单独设计、验证；尤其要先确认 `V1` 是否已在任何真实 MySQL 环境执行，再决定修改原迁移或新增后续迁移。

## 5. 参考来源

- 本仓库已确认的 Java 21、LiteFlow、场景流程设计。
- `D:\dmp\dmp-java-projects` 的公共实体字段惯例。
- 本地 gami-workflow 的 `references/code-spec/db.md`、`references/infra-spec/database.md`。它们的旧 Spring Boot、MyBatis Plus 和 Lombok 要求不作为本项目约束。
