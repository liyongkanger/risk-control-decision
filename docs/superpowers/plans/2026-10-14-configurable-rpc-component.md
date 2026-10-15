# 可配置只读 RPC 节点实施计划

> 历史计划：已由[变量中心直接重构实施计划](2026-10-15-variable-center-refactor.md)替代。

**Goal:** 管理员可在后台登记只读 HTTP 操作，流程可选择固定版本并把 JSON 结果用于后续判断。

**Architecture:** 数据库保存操作资产与不可变版本；组件目录登记通用 `http-json-get` 适配器；流程节点配置映射请求输入，发布时校验并固定操作版本；登记与更新操作集中在该组件节点面板。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus、Flyway、LiteFlow、React、Ant Design、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-14-configurable-rpc-component-design.md`

## Tasks

- [x] 建表、操作资产 Service/API 和分页接口；先写持久化与 HTTP 测试。
- [x] 实现通用只读 HTTP JSON 组件，输入映射与执行结果；先写本地假服务集成测试。
- [x] 接入发布校验及版本固定，保证发布不请求远端；验证失败场景。
- [x] 写接口文档，在流程组件节点面板接入操作登记、搜索、更新与映射。
- [x] 运行后端、前端全量验证；本地浏览器验证操作登记、更新、流程节点选择，集成测试验证执行成功与远端失败。
