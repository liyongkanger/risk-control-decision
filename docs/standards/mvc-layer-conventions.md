# Controller、Service、Mapper 分层开发规范

状态：已确认的分层约定。新增代码遵循本文件；现有代码在相关功能调整时逐步迁移。Java 基本风格、组件分包和表结构另见[项目开发规范](project-conventions.md)。

本规范从现有场景管理、流程发布和决策执行代码出发。参考项目中的 H5 响应、网关用户头、Apollo 和 FastJSON 不属于当前平台，不能作为默认模板。

## 1. 包与职责

先按业务领域分包，再在领域内按职责分层。当前 `policy`、`decision` 等领域可以逐步整理为：

```text
policy/
├── controller/         HTTP 接口
├── service/            对外业务用例接口
│   └── impl/           用例实现
├── mapper/             MyBatis 接口
├── repository/         多表读写及持久化对象转换（需要时）
├── vo/
│   ├── req/            HTTP 请求
│   └── resp/           HTTP 响应
├── dto/                内部传输对象，确有跨层传输时创建
├── bo/                 业务对象，确有独立业务语义时创建
└── assembler/          多处复用的对象转换
```

不为目录结构预建空包，不要求每个业务对象同时拥有 Req、Resp、Dto、Bo 四种形态。`component` 的框架能力按项目开发规范单独组织，不放进某个业务领域的 Mapper 或 Service 实现。

跨领域 HTTP 公共能力统一放在 `common/response`、`common/exception` 和 `common/web`，业务模块只使用这些类型，不自行定义另一套响应外壳。

| 层 | 负责 | 不负责 |
| --- | --- | --- |
| Controller | HTTP 参数接收、调用业务用例、返回接口响应 | 编排流程、写 SQL、直接操作 LiteFlow |
| Service | 校验业务条件、组织用例、调用 Repository 或 Mapper 和基础组件、确定业务结果 | 解析 HTTP 细节、拼装 HTTP 错误响应 |
| Mapper | SQL 与数据库字段映射 | 业务决策、远程调用、对象的 HTTP 展示格式 |

## 2. 依赖注入

本项目新增 Spring Bean 依赖统一使用 `@Resource` 字段注入，不使用 `@Autowired`。这是本项目的明确约定；已有构造注入在相关类调整时再迁移，不为格式单独批量改动。每个依赖按实际职责命名，不规定字段顺序。

```java
@Resource
private FlowPublicationService flowPublicationService;
```

非 Spring 对象通过构造方法显式传入依赖。类应避免循环依赖；遇到循环依赖先检查职责划分。

## 3. Controller

### 3.1 类、方法与路由

- 类名以 `Controller` 结尾，写中文职责注释，使用 `@RestController`；同一资源的公共路由写在类级 `@RequestMapping`。
- 按操作语义选择 HTTP 方法：查询用 `GET`，创建或执行动作用 `POST`，整体更新可用 `PUT`。场景流程的 `/draft`、`/validate`、`/publish` 可沿用已有路由语义。无需将所有接口改为 `POST`。
- 请求体使用独立的 `*Req` 类，响应使用 `*Resp` 类；路径、查询参数按其实际用途接收。Controller 不直接返回 Mapper 行对象，也不把 LiteFlow 内部类型作为接口契约。
- `@RequestHeader` 只接收本平台实际约定的请求头；`Locale` 只在需要国际化响应时使用。网关头常量及站点路径不能照搬参考项目。
- Controller 调用一个清楚的 Service 用例；请求转业务参数和业务结果转响应只做简单映射。复杂转换交给 Assembler。

### 3.2 方法形态

以下示例表达 Controller 的调用结构；`PublishFlowReq`、`PublishFlowResp` 已在当前仓库中定义。

```java
/** 发布场景流程。 */
@PostMapping("/publish")
public ApiResponse<PublishFlowResp> publish(@PathVariable String sceneCode,
                                            @RequestBody PublishFlowReq request) {
    log.info("[FlowAdminController][publish] sceneCode:{}", sceneCode);
    FlowVersion version = flowPublicationService.publish(sceneCode, request.getExpectedRevision());
    return ApiResponse.success(flowAssembler.toPublishResp(version));
}
```

Controller 方法不固定写两层 `try-catch`。业务异常和未知异常由 `common.exception.GlobalExceptionHandler` 统一处理；只有方法能在本地恢复、转换或补偿时才捕获异常。这样同一种错误不会在每个接口重复记录和映射。

### 3.3 响应与异常

所有业务接口使用 `common.response.ApiResponse<T>`：`code` 表示稳定结果码，`message` 表示可读说明，`data` 承载具体 `*Resp`。成功时使用 `ApiResponse.success(data)`；失败时由 `GlobalExceptionHandler` 映射结果码与 HTTP 状态。`/validate` 的图问题保留在 `data.issues`，其他操作抛出的图结构异常以 `data` 问题列表返回。未知异常返回通用错误，不把内部堆栈放入响应。请求体大小过滤器也按同一结构返回。结果码统一放在 `common.response.ResultCode`，不直接引入参考项目的 H5 专用响应类型。

## 4. Service

- 对外业务用例采用 `service/XxxService` 接口与 `service/impl/XxxServiceImpl` 实现；接口写中文职责和关键方法契约，实现使用 `@Service`。内部辅助类和简单算法类无需为了形式创建接口。
- Service 方法按“校验业务条件 → 获取所需数据 → 执行业务动作 → 构造业务结果”组织。复杂步骤提取为有业务含义的私有方法，如 `validatePublishRevision`、`buildExecutionContext`。
- Service 可以调用 Repository、Mapper 或基础组件的公开 `service`；不直接依赖 `component.liteflow.node`、`registry` 等内部实现。流程发布、执行等用例的边界保持清楚。
- 业务失败抛出有明确语义的异常，由统一异常入口转换为接口错误；不捕获后原样包装。确需补偿或转换异常时，在捕获处说明处理结果。
- 入口和关键状态变化记录必要标识及结果。不要在 Controller、Service 两层重复打印整个请求对象。

当前 `FlowPublicationService`、`DecisionExecutionService` 已拆为接口和 `impl` 实现；只在业务用例边界使用这种分离方式。

## 5. Mapper 与持久化对象

- Mapper 接口位于领域 `mapper` 包，命名为 `*Mapper`，使用 MyBatis `@Mapper`。方法名表达数据操作，例如 `findBySceneCode`、`insertVersion`、`switchPublishedVersion`。
- SQL 和字段映射在 Mapper 层维护；Mapper 返回持久化行对象或基础结果，Service 或专门的仓储适配类负责把它转换成业务对象。
- Controller 不调用 Mapper。业务模块不把 Mapper 行对象直接作为 HTTP 请求或响应。
- 新增或修改字段时，同步检查 SQL、结果映射和上层对象转换；表名及公共字段遵循[项目开发规范](project-conventions.md)。

## 6. Req、Resp、Dto、Bo

| 后缀 | 含义 | 示例 |
| --- | --- | --- |
| `Req` | HTTP 请求对象 | `PublishFlowReq` |
| `Resp` | HTTP 响应对象 | `PublishFlowResp` |
| `Dto` | 跨层或组件传输数据 | `FlowSnapshotDto` |
| `Bo` | 有独立业务语义的对象 | `ScenePolicyBo` |

请求与响应按领域放在 `vo/req` 和 `vo/resp`。类使用普通 Java 类，写中文类注释，不使用 `record`；只有某响应专用且体量很小的嵌套对象才用 `static` 内部类，否则单独建类。

### 6.1 Lombok 默认用法

项目默认引入 Lombok。新增的简单 `Req`、`Resp`、`Dto`、`Bo` 使用 `@Data` 生成访问方法；需要构建器的响应对象使用以下组合：

```java
import lombok.Data;

/** 流程发布请求。 */
@Data
public class PublishFlowReq {
    private long expectedRevision;
}
```

```java
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 流程发布响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishFlowResp {
    private long id;
    private String sceneCode;
    private long versionNumber;
    private String chainId;
}
```

`@Data` 会生成 `toString()`、`equals()` 和 `hashCode()`。含令牌、密码或个人信息的类对相应字段使用 `@ToString.Exclude`，必要时仅使用 `@Getter`、`@Setter`；有不变式的领域对象保留明确的构造方法和受控修改方法，不为了统一注解而开放所有 Setter。已有手写访问方法的类在相关功能修改时逐步收敛，不做纯格式批量替换。

## 7. Assembler

简单、只出现一次的转换可在边界处显式赋值；字段较多或多处复用时放入领域 `assembler` 包。方法名说明源和目标，如 `toPublishResp`、`toFlowVersion`。

MapStruct 目前不是项目依赖。以后接入时，用映射方法及必要的 `@Mapping` 表达字段转换；有复杂业务判断的转换用普通 Java 方法。不要创建只含 `default` 手工赋值方法的空 MapStruct 接口，也不预设静态 `MAPPER` 单例。新增字段时核对源对象、目标对象、Assembler 和调用方。

## 8. 基础组件与远程调用

Redis、HTTP/Feign 等基础能力经 `component.<能力名>.service` 暴露给业务 Service。业务层不直接依赖其客户端配置、连接对象或协议细节。Feign 与 Apollo 尚未接入；客户端注解、配置来源和异常转换在接入对应组件时再定，不把参考项目的 `fegin` 包名或 `*ConfigClient` 模板写成当前要求。

## 9. 日志与通用写法

- 有日志的类默认使用 Lombok `@Slf4j`。消息采用 `[ClassName][methodName]` 前缀，参数化占位输出，不做字符串拼接。
- `info` 记录请求标识、场景码、流程版本及关键状态变化；`warn` 记录可预期的校验或降级；`error` 记录需要排查的失败，异常对象作为最后一个参数。未知异常原则上由统一异常入口记录一次。
- 日志不默认序列化完整 `Req`、流程输入、节点配置或远程响应。避免令牌、密码、个人数据进入日志。当前项目使用 Jackson 处理 JSON，不因日志格式引入 FastJSON。
- 空值判断优先使用清楚的 JDK 写法；可空 `Boolean` 使用 `Boolean.TRUE.equals(flag)`。不为 `isBlank` 或集合判空单独引入工具依赖。
- 缩进、命名、中文注释及 Java 21/Java 8 风格以[项目开发规范](project-conventions.md)为准。

## 10. 应用顺序

本文件确定新增代码的分层、命名、依赖注入、响应结构、异常入口及日志边界。当前场景流程与同步决策代码已完成一轮分包和接口对象迁移；Lombok 已作为默认依赖接入。更细的业务错误码、MapStruct 和远程客户端的具体实现仍需后续设计。
