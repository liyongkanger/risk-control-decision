# Controller、Service、Mapper 分层开发规范

状态：项目约定。适用于后续新增的业务接口与分层代码；现有代码的迁移另行安排。

本文记录**写法与结构**。示例中的银行、交易、网关、H5、Apollo 等名称来自参考项目，不表示本项目已经接入这些系统。具体基础组件和依赖接入时再确定实现类。

## 1. 分层与包组织

按业务领域组织代码，在领域内划分职责：

```text
<domain>/
├── controller/       HTTP 接口
├── service/          业务接口
│   └── impl/         业务实现
├── mapper/           MyBatis 数据访问接口
├── vo/
│   ├── req/          请求对象
│   └── resp/         响应对象
├── dto/              内部传输对象
├── bo/               业务对象
└── assembler/        对象转换
```

- Controller 负责接收请求、调用 Service、包装响应；不直接调用 Mapper。
- Service 组织业务流程，可调用 Mapper 和基础组件提供的服务。
- Mapper 只负责数据库访问；不承担业务流程、HTTP 响应或远程调用。
- Redis、远程调用及其他基础能力由相应 `component.<能力名>.service` 提供，具体规范在组件接入时补充。

## 2. 依赖注入

统一使用 `@Resource` 字段注入，禁止使用 `@Autowired`。响应包装服务存在时，放在 Controller 的第一个注入字段。

```java
@Resource
private SceneService sceneService;
```

这条规则取代[项目开发规范](project-conventions.md)此前“优先使用构造方法注入”的写法；现有构造注入代码暂不因本次文档修改而调整。

## 3. Controller

### 3.1 类与路由

```java
/** 场景管理接口。 */
@Slf4j
@RestController
@RequestMapping("/api/scenes")
public class SceneController {
    @Resource
    private ResponseBoxService responseBoxService;

    @Resource
    private SceneService sceneService;
}
```

- 类使用 `@RestController`，需要日志时使用 `@Slf4j`；路由前缀按当前业务领域确定。
- 业务操作以 `@PostMapping` 为主；查询或更新接口可以根据语义使用 `@GetMapping`、`@PutMapping` 等。无需照搬参考项目的 `/{site}/{domain}/{action}` 路由。
- 请求对象使用 `@RequestBody XxxReq`。只有本项目网关实际提供用户 ID 请求头时，才加入相应的 `@RequestHeader`；只有接口需要国际化消息时，才接收 `Locale`。

### 3.2 方法结构

接口方法按“入口日志 → 调用 Service → 包装成功响应 → 分级处理业务异常和未知异常”的顺序编写。使用项目最终确定的响应包装服务和异常类型；以下模板展示结构，不要求现在创建同名类型。

```java
@PostMapping("/publish")
public ApiResponse<PublishSceneResp> publish(@RequestBody PublishSceneReq req, Locale locale) {
    log.info("[SceneController][publish] req:{}", JSON.toJSONString(req));
    try {
        PublishSceneResp resp = sceneService.publish(req);
        return responseBoxService.success(resp);
    } catch (BusinessException e) {
        log.error("[SceneController][publish] req:{}", JSON.toJSONString(req), e);
        return responseBoxService.fail(e.getCode(), locale);
    } catch (Exception e) {
        log.error("[SceneController][publish] req:{}", JSON.toJSONString(req), e);
        return responseBoxService.fail("FAILED", locale);
    }
}
```

当前仓库已有 `ApiErrorHandler` 统一处理异常，尚无 `ResponseBoxService`、`ApiResponse` 或 `BusinessException`。因此**模板不能直接复制进现有代码**；落地统一响应前，需先确定这些项目内类型与现有异常处理的关系。参考项目的 `MobiContentResponse<T>`、`MfException`、`h5Success`、`h5Fail` 仅表达此处的职责，不作为本项目的类名要求。

### 3.3 响应

目标是统一响应包装：包含业务数据、结果码和可读消息，泛型承载具体 `*Resp`。成功与失败通过同一包装服务生成。具体类名、错误码和 HTTP 状态语义随本项目 API 契约确定，避免与现有错误响应形成两套并行协议。

## 4. Service

业务服务采用接口和实现分离：`service/XxxService.java` 为无 Spring 注解的接口；`service/impl/XxxServiceImpl.java` 使用 `@Service`，需要日志时使用 `@Slf4j`。依赖按第 2 节注入。

```java
/** 场景发布业务服务。 */
public interface SceneService {
    PublishSceneResp publish(PublishSceneReq req);
}

/** 场景发布业务服务实现。 */
@Service
@Slf4j
public class SceneServiceImpl implements SceneService {
    @Resource
    private SceneMapper sceneMapper;

    @Override
    public PublishSceneResp publish(PublishSceneReq req) {
        log.info("[SceneServiceImpl][publish] req:{}", JSON.toJSONString(req));
        validateParameter(req);
        // 执行业务逻辑、转换结果并返回；业务失败抛出项目业务异常。
        return null;
    }

    private void validateParameter(PublishSceneReq req) {
        // 复杂校验按语义拆分。
    }
}
```

方法顺序为入口日志、参数与业务前置检查、业务执行、结果转换。复杂逻辑抽取为名称明确的私有方法。业务异常直接向上抛出，避免捕获后无意义地再次包装；项目异常类型在异常契约落地时确定。

## 5. Mapper

- Mapper 位于领域的 `mapper` 包，使用 MyBatis `@Mapper`；接口按业务表或清晰的聚合职责命名 `*Mapper`。
- SQL 及结果映射放在 Mapper 层；Controller 不依赖 Mapper，Service 不写散落的 SQL。
- Mapper 方法只表达数据库操作及其结果，不返回 HTTP 响应对象，不处理业务异常映射。
- 新增查询或字段时，同步检查 SQL、结果映射、Service 转换和响应字段。表命名、公共字段与逻辑删除规则以[项目开发规范](project-conventions.md)为准。

## 6. DTO、VO 与 BO

| 用途 | 后缀 | 示例 |
| --- | --- | --- |
| HTTP 请求 | `*Req` | `PublishSceneReq` |
| HTTP 响应 | `*Resp` | `PublishSceneResp` |
| 内部数据传输 | `*Dto` | `SceneVersionDto` |
| 业务对象 | `*Bo` | `ScenePolicyBo` |

请求与响应放在领域下的 `vo/req`、`vo/resp`。对象使用普通 Java 类，不使用 `record`。简单数据对象可使用 Lombok `@Data`；需要构建器时使用 `@Builder`，并按序列化、构造需要补充 `@NoArgsConstructor`、`@AllArgsConstructor`。只有某个响应专属的小型嵌套结构才使用 `public static` 内部类，并加中文类注释。

Lombok 尚未加入当前项目依赖；在接入前使用显式字段、构造方法和访问方法，不写无法编译的注解。

## 7. Assembler

对象转换集中在 `assembler` 包。需要多处复用或字段较多的映射使用 MapStruct；手工赋值的简单转换可用普通方法，不为调用 `default` 方法而创建 MapStruct 接口。

使用 MapStruct 时优先声明明确的映射方法与 `@Mapping`；若采用静态单例，命名为 `MAPPER`。新增字段必须逐层核对：请求/响应 VO、内部 DTO/BO、Assembler、Service 和 Controller 的转换入口。MapStruct 尚未接入仓库；接入时再确定 Spring 注入或静态单例的统一方式。

## 8. Feign 远程调用

后续接入时采用两层结构：`component.feign` 内定义远程接口，并通过配置类注册客户端；业务模块只通过该组件的 `service` 调用。远程接口使用实际选定的 Feign 风格，若使用原生 Feign，则采用 `@RequestLine`、`@Headers`。包名写作 `feign`，不沿用参考项目的 `fegin` 拼写。

客户端名可使用 `*ConfigClient`；URL 从项目配置读取。当前项目尚未接入 Feign 或 Apollo，不把参考项目的 Apollo 配置方式列为要求。

## 9. 异常与错误码

- 业务失败使用项目自定义运行时异常及内部错误码；Service 直接抛出。
- 面向客户端的错误码及国际化消息，与内部错误码建立明确映射；未知错误降级为通用失败码。
- 当前仓库已有 `ApiErrorHandler` 和 `ApiError`，具体业务异常、错误码枚举及响应包装尚待统一设计。参考项目的 `MfExceptionCodeEnum`、`H5ErrorCodeEnum` 和 `H5ResponseMapper` 只作结构示例。

## 10. 日志与通用写法

- 日志消息使用 `[ClassName][methodName]` 前缀；入口、关键业务事件用 `info`，可恢复问题和校验失败用 `warn`，异常用 `error` 且异常对象作为最后一个参数。
- 请求日志按接口需要记录；涉及敏感字段时先处理再记录。对象 JSON 序列化使用项目选定的统一工具。参考示例的 FastJSON `JSON.toJSONString` 暂不作为依赖要求。
- 缩进为 4 空格；类名使用 `UpperCamelCase` 加职责后缀，方法名使用动词开头的 `lowerCamelCase`，常量使用 `UPPER_SNAKE_CASE`。
- 可空 `Boolean` 判断使用 `Boolean.TRUE.equals(flag)`；集合、字符串判空使用项目统一选定的工具或 JDK 方法，不为单个判空引入新依赖。

## 11. 落地范围

本次只确立分层写法，不重构现有 Controller、Service、Mapper，也不引入 Lombok、MapStruct、Feign、FastJSON 或参考项目的响应类。新增代码遇到尚未确定的统一响应、异常、日志序列化工具时，先沿用当前项目可编译的实现，并在相关能力设计时统一。
