# 决策结果与业务动作：两个参考仓库的实现观察

日期：2026-10-13。以下是对本地代码的观察，不将参考仓库的实现直接作为本平台需求。

## `D:\dmp\risk-engine`：同步返回决策

- `internal/engine/engine.go` 定义 `Evaluate` 返回 `DecisionResult`；结果包含 `PASS / REJECT / MANUAL_REVIEW`、风险分、命中规则、原因及 `Actions`。`Actions` 的注释明确指向“建议执行的处置动作”。
- `api/http/v1/handler.go` 将这些字段返回给调用方。`internal/engine/decision_engine.go` 在同步执行后异步写审计记录，不在该入口执行拦截、发券等业务动作。
- 当前 `internal/orchestrator/pipeline.go` 的结果构建与聚合未给 `Actions` 赋值；`StepKindCustom` 虽有声明，当前 `dispatch` 也没有对应实现。因而“建议动作”在接口契约中已预留，但不能据此认定现有流程已能生成完整建议动作。

## `D:\dmp\dmp-java-projects`：异步流程中编排处置组件

- `dmp-decision-risk-async/.../controller/FlowController.java` 的 `/runPata` 调用异步 `FlowService.process` 后返回成功响应；`RiskFlowResponse` 当前为空。调用方在该接口不直接获得完整决策结论。
- `dmp-decision-risk-async/.../service/impl/FlowServiceImpl.java` 将请求放入 `FlowContext` 后执行流程，并更新流程状态。流程模板在 `.../inittestflow/InitAdakamiFlow.java` 中包含结果写入、风险用户记录和 `RabbitMqComponent` 等步骤。
- `dmp-common-component/.../plain/PataUserResultComponent.java` 调用服务保存流程结果；`RiskUserRecordComponent.java` 写入风险用户记录；`RabbitMqComponent.java` 发布消息。这些组件体现了在流程内执行持久化或通知动作。
- 上述处置组件实现 `DecisionDisposal`。`dmp-common-flow-core/.../flow/AbstractFlowNode.java` 在 span 执行路径中跳过这种组件；接口注释也说明 Beta 流程会据此跳过处置。可借鉴的是对有副作用组件进行明确标识，使特定运行模式可以控制其执行。
- `dmp-common-component/.../flowbase/FlowBaseComponent.java` 把一个 `Flow` 包装成 `FlowVoidComponent`，执行时调用 `flowProcessor.execute(flow.getFlowId(), context)`；`.../flow/subflow/manager/SubflowComponentRuntimeManager.java` 将子流程作为可管理的组件类型。这证明“大流程的一个节点调用子流程”在参考项目中有实现，但该包装组件返回 `Void`，不能直接照搬为本平台的业务决策结果契约。

## 对本平台产品方向的启发

先让调用方获得清楚的决策结果、原因和建议动作；业务动作能力随后通过节点组件接入。无论动作发生在流程内还是由调用方执行，产品都应区分“建议做什么”和“已经做了什么”。测试模拟或影子运行时，有副作用节点需要明确的执行规则。

当前项目的 `ExecutionResult` 只有执行 `status`、`nodeResults`、轨迹和错误码；`status` 表示流程执行状态，尚不是风控或营销业务决策结论。后续设计通用决策输出时需单独定义业务结论及其原因。
