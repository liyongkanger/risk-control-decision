import { useEffect, useState } from 'react'
import { getPublishedFlow, listFlows, type FlowPage, type PublishedFlow } from '../services/flow'
import type { FlowNode } from './types'
import { isVersionOutdated, readSubflowConfig, selectPublishedVersion, type InputSource, type SubflowConfig } from './subflowConfig'

type Props = {
  node: FlowNode
  ownerFlowCode: string
  onChange: (config: SubflowConfig) => void
}

/** 已发布子流程选择与显式输入映射。 */
export default function SubflowNodeInspector({ node, ownerFlowCode, onChange }: Props) {
  const config = readSubflowConfig(node.config)
  const [query, setQuery] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [flows, setFlows] = useState<FlowPage | null>(null)
  const [published, setPublished] = useState<PublishedFlow | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    void listFlows(keyword, page, 10).then((value) => {
      if (active) { setFlows(value); setError(null) }
    }).catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : '流程搜索失败') })
    return () => { active = false }
  }, [keyword, page])

  useEffect(() => {
    let active = true
    if (!config.flowCode) { setPublished(null); return }
    void getPublishedFlow(config.flowCode).then((value) => {
      if (active) { setPublished(value); setError(null) }
    }).catch((cause) => {
      if (active) { setPublished(null); setError(cause instanceof Error ? cause.message : '发布版本读取失败') }
    })
    return () => { active = false }
  }, [config.flowCode])

  const choose = async (flowCode: string) => {
    try {
      const value = await getPublishedFlow(flowCode)
      setPublished(value)
      setError(null)
      onChange(selectPublishedVersion(config, value))
    } catch (cause) { setError(cause instanceof Error ? cause.message : '发布版本读取失败') }
  }

  const refreshPublished = async () => {
    try { setPublished(await getPublishedFlow(config.flowCode)); setError(null) }
    catch (cause) { setError(cause instanceof Error ? cause.message : '发布版本读取失败') }
  }

  const changeMapping = (field: string, source: InputSource) => {
    onChange({ ...config, inputMappings: { ...config.inputMappings, [field]: source } })
  }

  const outdated = published ? isVersionOutdated(config, published) : false
  const requiredInputs = published && !outdated ? published.requiredInputs : Object.keys(config.inputMappings)

  return <div className="node-config-block">
    <h3>选择已发布流程</h3>
    <div className="flow-search"><input aria-label="搜索子流程" value={query} placeholder="流程码或名称"
      onChange={(event) => setQuery(event.target.value)} />
      <button type="button" onClick={() => { setPage(1); setKeyword(query) }}>搜索</button></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="flow-pick-list">
      {flows?.records.map((flow) => <div className="flow-pick-row" key={flow.flowCode}>
        <span>{flow.flowName}<small>{flow.flowCode} · {flow.publishedVersionNumber === null ? '未发布' : `v${flow.publishedVersionNumber}`}</small></span>
        <button type="button" disabled={flow.publishedVersionId === null || flow.flowCode === ownerFlowCode}
          onClick={() => void choose(flow.flowCode)}>选择</button>
      </div>)}
    </div>
    {flows && flows.total > 10 && <div className="flow-pages">
      <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
      <span>{page} / {Math.ceil(flows.total / 10)}</span>
      <button type="button" disabled={page * 10 >= flows.total} onClick={() => setPage(page + 1)}>下一页</button>
    </div>}
    {config.flowCode && <div className="selected-flow">
      <strong>{config.flowCode}</strong><span>固定版本 ID：{config.versionId || '未选择'}</span>
      {published && <span>当前发布：v{published.versionNumber} · ID {published.id}</span>}
      <button type="button" onClick={() => void refreshPublished()}>刷新版本状态</button>
      {outdated && <><p className="form-warning">子流程已有新发布版本。当前草稿仍固定使用 ID {config.versionId}；发布前需要显式升级。</p>
        <button type="button" onClick={() => onChange(selectPublishedVersion(config, published!))}>升级到当前版本</button></>}
    </div>}
    {config.flowCode && !outdated && <div className="mapping-fields"><h3>输入映射</h3>
      {requiredInputs.length === 0 && <p className="form-note">这个版本未声明必需输入。</p>}
      {requiredInputs.map((field) => {
        const mapping = config.inputMappings[field] ?? { source: 'REQUEST_INPUT' as const, key: '' }
        return <div className="mapping-row" key={field}>
          <strong>{field}</strong>
          <label>来源<select value={mapping.source} onChange={(event) => changeMapping(field,
            event.target.value === 'NODE_RESULT'
              ? { source: 'NODE_RESULT', nodeId: '', path: 'businessResult.conclusion' }
              : { source: 'REQUEST_INPUT', key: '' })}>
            <option value="REQUEST_INPUT">请求输入</option><option value="NODE_RESULT">上游节点结果</option>
          </select></label>
          {mapping.source === 'REQUEST_INPUT'
            ? <label>请求字段<input value={mapping.key} placeholder="如 userId"
              onChange={(event) => changeMapping(field, { source: 'REQUEST_INPUT', key: event.target.value })} /></label>
            : <><label>上游节点 ID<input value={mapping.nodeId} placeholder="如 eligibilityCall"
              onChange={(event) => changeMapping(field, { ...mapping, nodeId: event.target.value })} /></label>
              <label>结果路径<input value={mapping.path} placeholder="businessResult.conclusion"
                onChange={(event) => changeMapping(field, { ...mapping, path: event.target.value })} /></label></>}
        </div>
      })}
    </div>}
  </div>
}
