# RiskEngine 架构参考笔记

> 记录日期：2026-10-10  
> 参考仓库：`D:\dmp\risk-engine`，审阅时 HEAD 为 `cc92398`  
> 用途：记录可借鉴的架构思路；本文不是本项目的设计定稿。

## 1. 场景、策略集与执行管线分层

RiskEngine 根据请求的 `SceneCode` 查找 `PolicySet`，再由对应的 `Pipeline` 执行决策。一个策略集描述场景、版本、步骤、失败后的兜底结果，以及可选的灰度和实验配置。步骤声明类型、超时、失败策略、条件、参数映射等；执行器将步骤分发给名单、规则、模型等能力。

```text
DecisionRequest.SceneCode
  → Registry.Get(sceneCode)
  → PolicySet / Pipeline
  → LIST、RULE、MODEL 等步骤
  → DecisionResult
```

**借鉴价值：**业务场景负责选择策略，策略负责组织执行，具体能力负责计算。新增场景主要增加策略定义；新增能力主要增加步骤执行适配器。这让场景配置与执行实现各有清晰边界。

**本项目设计时再确定：**场景的唯一标识、策略版本及发布状态、步骤类型的扩展契约、跨场景共享节点、决策结果的通用结构。RiskEngine 当前的 `PASS / REJECT / MANUAL_REVIEW` 和风险分属于风控语义，不宜直接视为通用平台的固定输出。

源码入口：`D:\dmp\risk-engine\internal\orchestrator\adapter.go:26`、`internal\orchestrator\orchestrator.go:54`、`internal\orchestrator\orchestrator.go:109`、`internal\orchestrator\pipeline.go:423`。

## 2. 并发特征获取与原子策略替换

特征服务将不同数据来源封装成 `Fetcher`，并发获取，分别设置超时；单个来源失败时记录告警并返回其余特征。策略注册表先构建新的场景到管线映射，再通过原子指针整体替换，决策请求读取当前快照。

**借鉴价值：**数据来源可以独立扩展，慢来源不会串行拖慢其他来源；配置切换时，读取路径不需要逐请求加锁。

**本项目设计时再确定：**特征的类型、来源、时效和缺失状态；每个特征失败时是跳过、使用默认值还是终止；策略、规则和特征定义能否以同一个版本快照执行。不能仅用“缺失特征等于零值”表达所有降级结果。

源码入口：`D:\dmp\risk-engine\internal\feature\feature.go:76`、`internal\feature\feature.go:110`、`internal\orchestrator\registry.go:102`、`internal\orchestrator\registry.go:127`。

## 3. 执行控制、灰度与效果验证

步骤支持超时、重试和 `SKIP / REJECT / FALLBACK` 失败策略。策略集还包含 A/B、按稳定哈希分桶的灰度、影子策略及冠军挑战者配置。执行结果保留步骤轨迹；影子和挑战者结果用于比较，不改变正式返回的决策。

**借鉴价值：**将异常处理和策略验证放在执行框架中，业务步骤只需实现自身能力。灰度、影子运行和结果轨迹为策略上线与回溯提供基础。

**本项目设计时再确定：**实验分流键、版本固定方式、影子任务资源上限、审计记录的持久化与丢失策略，以及超时和兜底的默认值。执行轨迹应能关联输入摘要、策略版本、特征版本、节点结果和最终决策。

源码入口：`D:\dmp\risk-engine\internal\orchestrator\orchestrator.go:28`、`internal\orchestrator\pipeline.go:77`、`internal\orchestrator\pipeline.go:208`、`internal\orchestrator\pipeline.go:252`、`internal\orchestrator\pipeline.go:361`、`internal\engine\engine.go:61`。

## 阅读源码时的两个边界

1. 仓库把管线称为 DAG，但当前执行方式是顺序步骤加连续的并行步骤组，尚未表达任意节点依赖；参见 `internal\orchestrator\pipeline.go:708`。
2. 部分能力已有接口和配置，启动接线仍是占位实现：规则执行器为空实现，模型注册表未注册评分器；参见 `cmd\server\main.go:91`、`cmd\server\main.go:124`。参考架构时需区分设计意图与可运行能力。
