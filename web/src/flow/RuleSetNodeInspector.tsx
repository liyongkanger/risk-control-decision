import { useEffect, useState } from 'react'
import { getRuleSet, getRuleSetVersion, listRuleSets } from '../services/ruleCenter'
import type { RuleSetPage, RuleSetVersion } from '../services/ruleCenter'
import type { FlowNode } from './types'

type InputSource = { source: 'INPUT'; key: string } | { source: 'NODE_RESULT'; nodeId: string }
export type RuleSetNodeConfig = { ruleSetCode: string; ruleVersionId: number
  inputMappings: Record<string, InputSource> }
type Props = { node: FlowNode; requiredInputs: string[]; candidates: FlowNode[]
  onChange: (config: RuleSetNodeConfig) => void }

/** 选择规则中心的已发布版本并映射强类型输入。 */
export default function RuleSetNodeInspector({ node, requiredInputs, candidates, onChange }: Props) {
  const config: RuleSetNodeConfig = {
    ruleSetCode: typeof node.config.ruleSetCode === 'string' ? node.config.ruleSetCode : '',
    ruleVersionId: typeof node.config.ruleVersionId === 'number' ? node.config.ruleVersionId : 0,
    inputMappings: node.config.inputMappings && typeof node.config.inputMappings === 'object'
      ? node.config.inputMappings as Record<string, InputSource> : {},
  }
  const [query, setQuery] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [list, setList] = useState<RuleSetPage | null>(null)
  const [version, setVersion] = useState<RuleSetVersion | null>(null)
  const [currentId, setCurrentId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    let active = true
    void listRuleSets(keyword, page, 10).then((value) => { if (active) { setList(value); setError(null) } })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : '规则集读取失败') })
    return () => { active = false }
  }, [keyword, page])
  useEffect(() => {
    let active = true
    if (!config.ruleSetCode || !config.ruleVersionId) { setVersion(null); setCurrentId(null); return }
    void Promise.all([getRuleSetVersion(config.ruleVersionId), getRuleSet(config.ruleSetCode)])
      .then(([selected, current]) => { if (active) { setVersion(selected); setCurrentId(current.currentVersionId); setError(null) } })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : '规则版本读取失败') })
    return () => { active = false }
  }, [config.ruleSetCode, config.ruleVersionId])
  const choose = async (code: string, id: number) => {
    try {
      const selected = await getRuleSetVersion(id)
      setVersion(selected); setCurrentId(id)
      onChange({ ruleSetCode: code, ruleVersionId: id,
        inputMappings: Object.fromEntries(selected.definition.inputs.map((input) => [input.name,
          { source: 'INPUT', key: requiredInputs.includes(input.name) ? input.name : '' }])) })
    } catch (cause) { setError(cause instanceof Error ? cause.message : '规则版本读取失败') }
  }
  const variableNodes = candidates.filter((candidate) => candidate.componentKey === 'get-variable'
    && candidate.id !== node.id)
  return <div className="node-config-block">
    <h3>执行规则集</h3><p className="form-note">选择已发布规则版本；流程发布后固定该版本。</p>
    <div className="flow-search"><input aria-label="搜索规则集" value={query}
      onChange={(event) => setQuery(event.target.value)} />
      <button type="button" onClick={() => { setPage(1); setKeyword(query) }}>搜索</button></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="flow-pick-list">{list?.records.map((item) => <div className="flow-pick-row" key={item.code}>
      <span>{item.name}<small>{item.code} · {item.currentVersionId ? `版本 ID ${item.currentVersionId}` : '未发布'}</small></span>
      <button type="button" disabled={!item.currentVersionId}
        onClick={() => item.currentVersionId && void choose(item.code, item.currentVersionId)}>选择</button>
    </div>)}</div>
    {list && list.total > 10 && <div className="flow-pages">
      <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
      <span>{page} / {Math.ceil(list.total / 10)}</span>
      <button type="button" disabled={page * 10 >= list.total} onClick={() => setPage(page + 1)}>下一页</button>
    </div>}
    {config.ruleSetCode && <div className="selected-flow">
      <strong>{config.ruleSetCode}</strong><span>固定版本 ID：{config.ruleVersionId}</span>
      {currentId !== null && currentId !== config.ruleVersionId && <>
        <p className="form-warning">规则集已有新版本；重新发布流程前需显式升级。</p>
        <button type="button" onClick={() => void choose(config.ruleSetCode, currentId)}>升级到当前版本</button>
      </>}
      {version?.definition.inputs.map((input) => {
        const mapping = config.inputMappings[input.name] ?? { source: 'INPUT', key: '' }
        return <div key={input.name}>
          <label>{input.name} ({input.type}) 来源
            <select value={mapping.source} onChange={(event) => onChange({ ...config,
              inputMappings: { ...config.inputMappings, [input.name]: event.target.value === 'NODE_RESULT'
                ? { source: 'NODE_RESULT', nodeId: '' } : { source: 'INPUT', key: '' } } })}>
              <option value="INPUT">流程输入</option><option value="NODE_RESULT">上游获取变量</option>
            </select>
          </label>
          {mapping.source === 'INPUT' ? <label>流程输入字段
            <select value={mapping.key} onChange={(event) => onChange({ ...config,
              inputMappings: { ...config.inputMappings, [input.name]: { source: 'INPUT', key: event.target.value } } })}>
              <option value="">请选择</option>{requiredInputs.map((name) => <option key={name} value={name}>{name}</option>)}
            </select></label> : <label>上游变量节点
            <select value={mapping.nodeId} onChange={(event) => onChange({ ...config,
              inputMappings: { ...config.inputMappings, [input.name]: { source: 'NODE_RESULT', nodeId: event.target.value } } })}>
              <option value="">请选择</option>{variableNodes.map((candidate) =>
                <option key={candidate.id} value={candidate.id}>{candidate.id}</option>)}
            </select></label>}
        </div>
      })}
    </div>}
  </div>
}
