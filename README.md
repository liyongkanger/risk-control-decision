# 风控决策平台

项目使用 Java 21 和 Spring Boot 4.1。当前包含启动类、MyBatis、MySQL、Redis 和 Actuator 的基础接线，尚未定义业务接口与数据库表。

## 本地配置

默认启用 `local` profile。参考 `collection-agent` 的配置组织方式，MySQL 和 Redis 的本地连接参数放在 `application.yml` 的 `local` 配置段中；真实连接参数仍从环境变量读取，不在仓库保存凭据。其他环境需提供自己的 profile 配置。

| 环境变量 | 默认值 |
|---|---|
| `RISK_DB_URL` | `jdbc:mysql://localhost:3306/risk_control_decision?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai` |
| `RISK_DB_USERNAME` | `root` |
| `RISK_DB_PASSWORD` | 空 |
| `RISK_REDIS_HOST` | `localhost` |
| `RISK_REDIS_PORT` | `6379` |
| `RISK_REDIS_PASSWORD` | 空 |
| `RISK_REDIS_DATABASE` | `0` |

当前由 Spring Boot 管理的 MySQL Connector/J 为 9.7，目标 MySQL Server 8.0 及以上；部署版本确定后再核对驱动兼容性。

准备好 MySQL 数据库和 Redis 后，使用 JDK 21 运行 `mvn spring-boot:run`。Actuator 提供 `/actuator/health` 和 `/actuator/info`。没有业务接口、策略配置或数据库迁移脚本。
