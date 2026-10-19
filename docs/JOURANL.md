# 学习与开发记录

> 慢慢来，比较快。先想清业务场景和模块，再逐步细化代码。

这里记录开发过程中的问题、调整和想法；逐日进展见[七天时间线](day-by-day/README.md)。

## 从骨架走到业务闭环

```mermaid
flowchart LR
    A[服务骨架与后台 UI] --> B[场景与流程解耦]
    B --> C[流程发布与子流程]
    C --> D[变量中心与规则中心]
    D --> E[结算优惠样板]
    E --> F[决策追溯与反馈]
```

原笔记的 Day 1、Day 2、Day 3 是思考阶段，不严格对应某个自然日。

## 问题 1：流程为什么要从场景里拆出来

```mermaid
flowchart LR
    subgraph old["早期"]
        A[先创建场景] --> B[在场景中设计流程]
        B --> C[复用时容易复制流程]
    end
    subgraph current["现在"]
        D[独立设计并发布流程] --> E[场景 A 绑定版本]
        D --> F[场景 B 绑定版本]
    end
    C -->|调整| D
```

场景负责选择入口；流程成为可复用资产。发布新版本后，已有场景仍使用原来绑定的版本，需显式切换。

## 问题 2：取数能力放在哪里

```mermaid
flowchart LR
    A[请求输入] --> D[变量中心：名称、类型、来源、版本]
    B[上游节点结果] --> D
    C[外部 HTTP 等来源] --> D
    D --> E[获取变量节点]
    D --> F[规则集引用变量]
    E --> G[流程判断]
    F --> G
```

最初担心 RPC、数据库等调用直接散落在组件配置里。现在由变量中心管理业务数据的取值契约，流程和规则引用固定版本；现有外部取值能力有明确边界，并非任意 RPC、数据库都已接入。

## 用营销样板检查设计

```mermaid
flowchart LR
    A[进入结算页] --> B[资格子流程]
    B --> C[优惠选择子流程]
    C --> D[外层流程给出最终建议]
    D --> E[模拟结算]
    E --> F[实际用券、支付、退款]
    F --> G[比较三组利润与转化]
    G -.->|人工复盘| B
```

建议用券、实际用券和最终利润分别记录。固定样本用于检查链路与指标计算，不代表真实营销效果。

## 还没解决好的问题

```mermaid
flowchart TD
    A[后续继续研究] --> B[前端组件配置 UI 怎样更易懂]
    A --> C[前后端接口交接怎样更顺畅]
    A --> D[AI 辅助开发怎样保持代码简洁]
    A --> E[AI Agent 在风控决策中能做什么]
```

前后端已有接口文档和页面联调记录；原笔记里设想的“中间层”、更好的前端 AI 能力与协作方式仍待探索。AI 写代码时，也要避免冗余实现，自己需要能解释代码。

## 以后可探索的能力

```mermaid
flowchart LR
    A[待探索] --> B[影子并行]
    A --> C[差异双跑]
    A --> D[空跑]
    A --> E[组件热更新]
    A --> F[数据驱动的智能决策]
```

这些是想法，不是当前 PRD 承诺或已实现功能。下一步仍应先明确业务目标、数据来源和验收指标。

参考：[v0.1 决策子流程 PRD](../prd/iterations/2026-10-v0.1-decision-subflow/prd.md) · [结算优惠场景](../prd/scenarios/checkout-offer-incremental-profit.md) · [流程发布代码](../src/main/java/com/dmp/riskdecision/policy/service/impl/FlowAssetServiceImpl.java) · [决策执行代码](../src/main/java/com/dmp/riskdecision/decision/service/impl/DecisionExecutionServiceImpl.java)

延伸阅读：[风控资料汇总](https://github.com/yangliang1415/awesome-risk-control) · [美团风控系统经验](https://tech.meituan.com/2017/01/13/risk-control-system-experience-sharing.html)
