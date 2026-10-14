import { useEffect, useState } from 'react'
import type { FlowNode } from './types'
import { parseJsonObject } from './subflowConfig'

type Props = { node: FlowNode; onChange: (config: Record<string, unknown>) => void }

/** 显式业务结果配置，不把建议动作当作已执行动作。 */
export default function ResultNodeInspector({ node, onChange }: Props) {
  const config = node.config
  const action = config.suggestedAction && typeof config.suggestedAction === 'object'
    ? config.suggestedAction as Record<string, unknown> : null
  const [detailsText, setDetailsText] = useState('{}')
  const [parametersText, setParametersText] = useState('{}')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setDetailsText(JSON.stringify(config.details ?? {}, null, 2))
    setParametersText(JSON.stringify(action?.parameters ?? {}, null, 2))
    setError(null)
  }, [node.id])

  const update = (field: string, value: unknown) => onChange({ ...config, [field]: value })
  const applyObjects = () => {
    try {
      const details = parseJsonObject(detailsText)
      const parameters = parseJsonObject(parametersText)
      onChange({ ...config, details,
        suggestedAction: action && typeof action.code === 'string' && action.code.trim()
          ? { code: action.code, parameters } : null })
      setError(null)
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'JSON 格式错误') }
  }

  return <div className="node-config-block"><h3>最终业务结果</h3>
    <label>结论代码<input value={String(config.conclusion ?? '')} placeholder="如 NO_NEW_OFFER"
      onChange={(event) => update('conclusion', event.target.value)} /></label>
    <label>原因代码<input value={String(config.reasonCode ?? '')} placeholder="如 NOT_ELIGIBLE"
      onChange={(event) => update('reasonCode', event.target.value)} /></label>
    <label>原因说明<textarea value={String(config.reason ?? '')} rows={2}
      onChange={(event) => update('reason', event.target.value)} /></label>
    <label>业务明细 JSON<textarea value={detailsText} rows={4}
      onChange={(event) => setDetailsText(event.target.value)} /></label>
    <label>建议动作代码（可选）<input value={String(action?.code ?? '')} placeholder="如 APPLY_OFFER"
      onChange={(event) => onChange({ ...config,
        suggestedAction: event.target.value ? { code: event.target.value,
          parameters: action?.parameters ?? {} } : null })} /></label>
    <label>建议动作参数 JSON<textarea value={parametersText} rows={3}
      onChange={(event) => setParametersText(event.target.value)} /></label>
    <button type="button" onClick={applyObjects}>应用明细与动作参数</button>
    {error && <p className="form-error" role="alert">{error}</p>}
    <p className="form-note">建议动作只随决策返回，实际是否执行由业务系统反馈。</p>
  </div>
}
