import { useEffect, useState } from 'react'
import { getVariable, getVariableVersion, listVariables } from '../services/variableCenter'
import type { VariablePage, VariableVersion } from '../services/variableCenter'
import type { FlowNode } from './types'

export type VariableNodeConfig = { variableCode: string; versionId: number }
type Props = { node: FlowNode; onChange: (config: VariableNodeConfig) => void }

/** 从变量中心选择固定版本；依赖由变量中心解析。 */
export default function VariableNodeInspector({ node, onChange }: Props) {
  const config: VariableNodeConfig = {
    variableCode: typeof node.config.variableCode === 'string' ? node.config.variableCode : '',
    versionId: typeof node.config.versionId === 'number' ? node.config.versionId : 0,
  }
  const [query, setQuery] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [list, setList] = useState<VariablePage | null>(null)
  const [selectedVersion, setSelectedVersion] = useState<VariableVersion | null>(null)
  const [currentVersion, setCurrentVersion] = useState<VariableVersion | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    void listVariables(keyword, page, 10).then((result) => {
      if (active) { setList(result); setError(null) }
    }).catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : '变量列表读取失败') })
    return () => { active = false }
  }, [keyword, page])

  useEffect(() => {
    let active = true
    if (!config.variableCode || !config.versionId) { setSelectedVersion(null); setCurrentVersion(null); return }
    void Promise.all([getVariableVersion(config.versionId), getVariable(config.variableCode)])
      .then(([selected, current]) => {
        if (active) { setSelectedVersion(selected); setCurrentVersion(current); setError(null) }
      }).catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : '变量版本读取失败') })
    return () => { active = false }
  }, [config.variableCode, config.versionId])

  const choose = (variable: VariableVersion) => {
    setSelectedVersion(variable)
    setCurrentVersion(variable)
    onChange({ variableCode: variable.variableCode, versionId: variable.id })
  }

  return <div className="node-config-block">
    <h3>获取变量</h3>
    <p className="form-note">先在“变量中心”定义强类型变量，再在这里选择版本。结果是单个值。</p>
    <div className="flow-search"><input aria-label="搜索变量" value={query}
      placeholder="变量码或名称" onChange={(event) => setQuery(event.target.value)} />
      <button type="button" onClick={() => { setPage(1); setKeyword(query) }}>搜索</button></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="flow-pick-list">{list?.records.map((variable) =>
      <div className="flow-pick-row" key={variable.variableCode}>
        <span>{variable.variableName}<small>{variable.variableCode} · {variable.valueType} · v{variable.versionNumber}</small></span>
        <button type="button" onClick={() => choose(variable)}>选择</button>
      </div>)}</div>
    {list && list.total > 10 && <div className="flow-pages">
      <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
      <span>{page} / {Math.ceil(list.total / 10)}</span>
      <button type="button" disabled={page * 10 >= list.total} onClick={() => setPage(page + 1)}>下一页</button>
    </div>}
    {config.variableCode && <div className="selected-flow">
      <strong>{config.variableCode}</strong>
      <span>固定版本 ID：{config.versionId || '未选择'} · 类型：{selectedVersion?.valueType ?? '读取中'}</span>
      {currentVersion && <span>当前版本：v{currentVersion.versionNumber} · ID {currentVersion.id}</span>}
      {currentVersion && currentVersion.id !== config.versionId && <>
        <p className="form-warning">变量已有新版本。重新发布前需要显式升级。</p>
        <button type="button" onClick={() => choose(currentVersion)}>升级到当前版本</button>
      </>}
    </div>}
  </div>
}
