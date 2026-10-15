import { useState } from 'react'
import { getVariableVersion } from '../services/variableCenter'
import type { FlowNode } from './types'
import type { VariableValueType } from '../services/variableCenter'

type Props = { node: FlowNode; candidates: FlowNode[]; onChange: (config: Record<string, unknown>) => void }
const operators = [
  { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' },
  { value: 'GT', label: '大于' }, { value: 'GTE', label: '大于等于' },
  { value: 'LT', label: '小于' }, { value: 'LTE', label: '小于等于' },
]

/** 对获取变量节点输出的单值配置类型安全比较。 */
export default function CompareVariableInspector({ node, candidates, onChange }: Props) {
  const [error, setError] = useState<string | null>(null)
  const config = node.config
  const type = String(config.valueType ?? 'STRING') as VariableValueType
  const available = type === 'DECIMAL' ? operators : operators.slice(0, 2)
  const changeType = (next: VariableValueType) => onChange({ ...config, valueType: next,
    operator: 'EQ', expected: next === 'BOOLEAN' ? false : next === 'DECIMAL' ? 0 : '' })
  const selectSource = async (sourceId: string) => {
    const source = candidates.find((candidate) => candidate.id === sourceId)
    const id = source?.config.versionId
    if (typeof id !== 'number' || id < 1) {
      onChange({ ...config, sourceNodeId: sourceId })
      return
    }
    try {
      const variable = await getVariableVersion(id)
      const next = variable.valueType
      onChange({ ...config, sourceNodeId: sourceId, valueType: next, operator: 'EQ',
        expected: next === 'BOOLEAN' ? false : next === 'DECIMAL' ? 0 : '' })
      setError(null)
    } catch (cause) { setError(cause instanceof Error ? cause.message : '变量版本读取失败') }
  }
  return <div className="node-config-block">
    <h3>比较变量</h3>
    {error && <p className="form-error" role="alert">{error}</p>}
    <label>上游获取变量节点<select value={String(config.sourceNodeId ?? '')}
      onChange={(event) => void selectSource(event.target.value)}>
      <option value="">请选择</option>
      {candidates.filter((candidate) => candidate.componentKey === 'get-variable').map((candidate) =>
        <option key={candidate.id} value={candidate.id}>{candidate.id}</option>)}
    </select></label>
    <label>变量类型<select value={type} onChange={(event) => changeType(event.target.value as VariableValueType)}>
      <option value="STRING">STRING</option><option value="BOOLEAN">BOOLEAN</option>
      <option value="DECIMAL">DECIMAL</option>
    </select></label>
    <label>比较操作<select value={String(config.operator ?? 'EQ')}
      onChange={(event) => onChange({ ...config, operator: event.target.value })}>
      {available.map((operator) => <option key={operator.value} value={operator.value}>{operator.label}</option>)}
    </select></label>
    {type === 'BOOLEAN' ? <label>预期值<select value={String(config.expected ?? false)}
      onChange={(event) => onChange({ ...config, expected: event.target.value === 'true' })}>
      <option value="true">true</option><option value="false">false</option>
    </select></label> : <label>预期值<input type={type === 'DECIMAL' ? 'number' : 'text'}
      step={type === 'DECIMAL' ? 'any' : undefined} value={String(config.expected ?? '')}
      onChange={(event) => onChange({ ...config,
        expected: type === 'DECIMAL' ? Number(event.target.value) : event.target.value })} /></label>}
    <p className="form-note">变量类型须与上游变量版本一致；发布校验会核对。</p>
  </div>
}
