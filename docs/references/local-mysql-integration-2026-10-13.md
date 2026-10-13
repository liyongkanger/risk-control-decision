# 本地 MySQL 联调记录

日期：2026-10-13  
数据库：本机 MySQL 5.7.40，独立 schema `risk_decision_it_20261013054602`。

## 实测结果

- Spring Boot 4.1.1 使用当前 MyBatis-Plus starter 与 MySQL Connector/J 成功启动，`GET /api/scenes` 返回 200。
- Flyway 在空库中依次执行 V1、V2、V3；重启后识别当前版本为 V3，没有重复执行迁移。
- 三张业务表 `tb_scene`、`tb_flow_draft`、`tb_flow_version` 均为 InnoDB、`utf8mb4`，各自具备 `id`、`inserttime`、`updatetime`、`isactive` 四个公共字段。
- 通过后台页面创建 `mysqlRealScene`，载入示例模板、保存草稿 r1、校验、发布 v1、试运行；执行结果为 `SUCCEEDED`，节点 `emit` 返回 `demo`。
- 数据库中保留场景、草稿和版本，场景发布指针指向 v1，三表相关记录的 `isactive` 均为 1。
- 重启后再次执行 `mysqlRealScene` 返回 `SUCCEEDED`；场景名称和描述的更新、搜索也通过。

## 环境边界

- 本机可用数据库是 **MySQL 5.7.40**。项目目标版本为 MySQL 8 及以上；这次实测不能代替 MySQL 8 验证。
- 本机没有运行 Redis，`/actuator/health` 因连接 `localhost:6379` 失败返回 **503 / DOWN**。当前场景、流程发布和决策执行链路不使用 Redis，相关业务接口在此环境下通过。
- 联调使用独立 schema，未修改原有业务数据库；schema 暂时保留，供复查。连接密码未写入仓库。
