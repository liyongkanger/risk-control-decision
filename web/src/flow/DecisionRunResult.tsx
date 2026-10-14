import type { DecisionRunResult as RunResult } from './api'

type ChildCall = {
  flowCode: string
  versionId: number
  status: string
  businessResult?: { conclusion?: string; reasonCode?: string; reason?: string;
    details?: Record<string, unknown>; suggestedAction?: { code: string } }
  errorCode?: string
  childTrace?: Array<{ nodeId: string; errorCode?: string }>
}

function isChildCall(value: unknown): value is ChildCall {
  return Boolean(value && typeof value === 'object' && 'flowCode' in value
    && 'versionId' in value && 'status' in value)
}

/** 分开展示外层结论、子流程调用和运行错误。 */
export default function DecisionRunResult({ result }: { result: RunResult }) {
  const calls = Object.entries(result.nodeResults).filter((entry): entry is [string, ChildCall] => isChildCall(entry[1]))
  return <section className="run-result" aria-label="决策试运行结果">
    <h2>试运行结果</h2>
    <p>请求 {result.requestId} · 外层版本 ID {result.versionId} · 运行 {result.status}</p>
    {result.status === 'FAILED' ? <p className="form-error" role="alert">执行失败：{result.errorCode ?? '未知错误'}。没有产生正常业务结论。</p>
      : result.businessResult ? <div className="run-conclusion"><strong>外层结论：{result.businessResult.conclusion}</strong>
        <p>{result.businessResult.reasonCode} · {result.businessResult.reason}</p>
        {Object.keys(result.businessResult.details ?? {}).length > 0
          && <details><summary>外层业务明细</summary><pre>{JSON.stringify(result.businessResult.details, null, 2)}</pre></details>}
        {result.businessResult.suggestedAction && <p>建议动作：{result.businessResult.suggestedAction.code}（未执行）</p>}
      </div> : <p className="form-warning">该发布版本未定义业务结果。</p>}
    {calls.length > 0 && <div className="run-calls"><h3>子流程调用</h3>
      {calls.map(([nodeId, call]) => <div className="run-call" key={nodeId}>
        <strong>{nodeId} · {call.flowCode} · 版本 ID {call.versionId}</strong>
        <p>运行 {call.status}{call.errorCode ? ` · ${call.errorCode}` : ''}</p>
        {call.businessResult && <><p>子流程结论：{call.businessResult.conclusion ?? '无'} · {call.businessResult.reasonCode ?? ''} · {call.businessResult.reason ?? ''}</p>
          {Object.keys(call.businessResult.details ?? {}).length > 0
            && <details><summary>子流程业务明细</summary><pre>{JSON.stringify(call.businessResult.details, null, 2)}</pre></details>}
          {call.businessResult.suggestedAction && <p>子流程建议：{call.businessResult.suggestedAction.code}（未执行）</p>}</>}
        {call.childTrace?.filter((trace) => trace.errorCode).map((trace) =>
          <p key={trace.nodeId} className="form-error">失败节点：{trace.nodeId} · {trace.errorCode}</p>)}
      </div>)}
    </div>}
  </section>
}
