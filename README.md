# 风控决策平台

项目使用 Java 21 和 Spring Boot 4.1。当前包含启动类、MyBatis、MySQL、Redis 和 Actuator 的基础接线，尚未定义业务接口与数据库表。

## 本地配置

连接参数从环境变量读取。`application.yml` 中的默认值仅供本地开发使用。

| 环境变量 | 默认值 |
|---|---|
| `RISK_DB_URL` | `jdbc:mysql://localhost:3306/risk_control_decision` |
| `RISK_DB_USERNAME` | `root` |
| `RISK_DB_PASSWORD` | 空 |
| `RISK_REDIS_HOST` | `localhost` |
| `RISK_REDIS_PORT` | `6379` |
| `RISK_REDIS_PASSWORD` | 空 |
| `RISK_REDIS_DATABASE` | `0` |

当前由 Spring Boot 管理的 MySQL Connector/J 为 9.7，目标 MySQL Server 8.0 及以上；部署版本确定后再核对驱动兼容性。

准备好 MySQL 数据库和 Redis 后，使用 JDK 21 运行 `mvn spring-boot:run`。Actuator 提供 `/actuator/health` 和 `/actuator/info`。没有业务接口、策略配置或数据库迁移脚本。
