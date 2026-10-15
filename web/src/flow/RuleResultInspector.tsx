import { useEffect, useState } from 'react'
import { getRuleSetVersion } from '../services/ruleCenter'
import type { RuleSetVersion } from '../services/ruleCenter'
import type { FlowNode } from './types'

export type RuleResultConfig = { sourceNodeId: string; expectedResultCode: string }
type Props = { node: FlowNode; candidates: FlowNode[]; onChange: (config: RuleResultConfig) => void }

/** 从规则节点声明的结果码中选择条件分支值。 */
export default function RuleResultInspector({ node, candidates, onChange }: Props) {
  const config: RuleResultConfig = {
    sourceNodeId: typeof node.config.sourceNodeId === 'string' ? node.config.sourceNodeId : '',
    expectedResultCode: typeof node.config.expectedResultCode === 'string' ? node.config.expectedResultCode : '',
  }
  const source = candidates.find((candidate) => candidate.id === config.sourceNodeId
    && candidate.componentKey === 'execute-rule-set')
  const [version, setVersion] = useState<RuleSetVersion | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    let active = true
    const id = source?.config.ruleVersionId
    if (typeof id !== 'number' || !id) { setVersion(null); return }
    void getRuleSetVersion(id).then((value) => { if (active) { setVersion(value); setError(null) } })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : '规则版本读取失败') })
    return () => { active = false }
  }, [source?.config.ruleVersionId])
  return <div className="node-config-block"><h3>规则结果判断</h3>
    {error && <p role="alert" className="form-error">{error}</p>}
    <label>上游规则节点<select value={config.sourceNodeId}
      onChange={(event) => onChange({ sourceNodeId: event.target.value, expectedResultCode: '' })}>
      <option value="">请选择</option>{candidates.filter((candidate) => candidate.componentKey === 'execute-rule-set')
        .map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.id}</option>)}
    </select></label>
    <label>预期结果码<select value={config.expectedResultCode}
      onChange={(event) => onChange({ ...config, expectedResultCode: event.target.value })}>
      <option value="">请选择</option>{version?.definition.resultCodes.map((code) =>
        <option key={code} value={code}>{code}</option>)}
    </select></label>
  </div>
}
