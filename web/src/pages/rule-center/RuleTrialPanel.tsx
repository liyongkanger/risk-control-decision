import { Alert, Button, Card, Input, InputNumber, Radio, Select, Tag, Typography } from 'antd'
import type { RuleEvaluation } from '../../services/ruleCenter'
import type { RuleTrialField } from './ruleTestSamples'

type TrialTarget = 'DRAFT' | 'PUBLISHED'
export type TrialDisplayResult = { evaluation: RuleEvaluation; versionLabel: string }
type Props = {
  fields: RuleTrialField[]; requestText: string; contextText: string
  onRequestTextChange: (text: string) => void; onContextTextChange: (text: string) => void
  onFieldChange: (field: RuleTrialField, value: string | boolean) => void
  loading: boolean; busy: boolean; hasSavedDraft: boolean; draftDirty: boolean
  target: TrialTarget; onTargetChange: (value: TrialTarget) => void
  versionId: number | null; onVersionIdChange: (value: number | null) => void
  versionFieldsReady: boolean; onLoadPublishedFields: () => void
  onRunDraft: () => void; onRunPublished: () => void
  result: TrialDisplayResult | null; error: string | null
}

const parseObject = (text: string): Record<string, unknown> | null => {
  try {
    const value: unknown = JSON.parse(text)
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, unknown> : null
  } catch { return null }
}

/** 按取值来源填写规则试算数据，并展示本次命中结果。 */
export default function RuleTrialPanel({ fields, requestText, contextText, onRequestTextChange,
  onContextTextChange, onFieldChange, loading, busy, hasSavedDraft, draftDirty,
  target, onTargetChange, versionId, onVersionIdChange, onRunDraft, onRunPublished,
  versionFieldsReady, onLoadPublishedFields, result, error }: Props) {
  const requestValues = parseObject(requestText)
  const contextValues = parseObject(contextText)
  const requestFields = fields.filter((field) => field.source === 'REQUEST_INPUT')
  const contextFields = fields.filter((field) => field.source === 'FLOW_CONTEXT')
  const canRun = hasSavedDraft && !busy && !loading
    && requestValues !== null && contextValues !== null
    && (target === 'DRAFT' ? !draftDirty : !!versionId && versionFieldsReady)
  const valueFor = (field: RuleTrialField) => (field.source === 'REQUEST_INPUT'
    ? requestValues : contextValues)?.[field.code]
  const renderFields = (items: RuleTrialField[]) => items.map((field) => {
    const value = valueFor(field)
    return <label className="rule-trial-field" key={`${field.source}:${field.code}`}>
      <span className="rule-trial-field-heading"><strong>{field.code}</strong>
        <span>{field.valueType === 'DECIMAL' ? '数值' : field.valueType === 'BOOLEAN' ? '布尔' : '文本'}</span></span>
      {field.valueType === 'BOOLEAN' ? <Select aria-label={`试算字段 ${field.code}`}
        value={value === true ? 'true' : value === false ? 'false' : undefined}
        placeholder="选择 true 或 false" disabled={loading || busy}
        options={[{ value: 'true', label: 'true' }, { value: 'false', label: 'false' }]}
        onChange={(selected) => onFieldChange(field, selected === 'true')} />
        : <Input aria-label={`试算字段 ${field.code}`}
          placeholder={field.valueType === 'DECIMAL' ? '输入十进制数' : '输入本次试算值'}
          value={value == null ? '' : String(value)} disabled={loading || busy}
          onChange={(event) => onFieldChange(field, event.target.value)} />}
    </label>
  })

  return <Card size="small" title="试算规则集" className="rule-trial-card">
    <Typography.Paragraph type="secondary" className="rule-trial-intro">
      只检查这份规则会命中哪一行及返回什么；不发布规则，也不执行完整流程。
    </Typography.Paragraph>
    <div className="rule-trial-step"><span>1</span><strong>填写本次试算数据</strong></div>
    {loading && <Typography.Text type="secondary">正在读取变量定义…</Typography.Text>}
    {(requestValues === null || contextValues === null) && <Alert type="warning" showIcon
      title="高级 JSON 格式无效" description="请展开下方高级编辑修正，再继续试算。"
      style={{ marginBottom: 12 }} />}
    {!loading && requestFields.length > 0 && <div className="rule-trial-group">
      <div className="rule-trial-group-title">请求输入
        <span>按变量码填写本次请求中的值</span></div>
      <div className="rule-trial-fields">{renderFields(requestFields)}</div>
    </div>}
    {!loading && contextFields.length > 0 && <div className="rule-trial-group">
      <div className="rule-trial-group-title">流程上下文临时值
        <span>仅用于本次单独试算；正式执行读取上游节点结果</span></div>
      <div className="rule-trial-fields">{renderFields(contextFields)}</div>
    </div>}
    {!loading && fields.length === 0 && <Typography.Paragraph type="secondary">
      当前规则没有需要手填的请求或流程上下文值。
    </Typography.Paragraph>}
    <Typography.Paragraph type="secondary" className="rule-trial-source-note">
      HTTP 和营销样板等外部变量会真实取值；请填写能查到数据的定位字段。
    </Typography.Paragraph>
    <details className="rule-trial-advanced">
      <summary>高级编辑：查看或补充 JSON</summary>
      <div className="rule-trial-json-grid">
        <label>请求输入 JSON <Input.TextArea aria-label="试算输入 JSON" value={requestText}
          rows={4} disabled={loading || busy} onChange={(event) => onRequestTextChange(event.target.value)} /></label>
        <label>流程上下文临时样例 JSON <Input.TextArea aria-label="临时流程上下文样例 JSON"
          value={contextText} rows={4} disabled={loading || busy}
          onChange={(event) => onContextTextChange(event.target.value)} /></label>
      </div>
      <Typography.Text type="secondary">精确小数请写成 JSON 字符串，避免浏览器舍入。</Typography.Text>
    </details>

    <div className="rule-trial-step rule-trial-target-step"><span>2</span><strong>选择试算对象</strong></div>
    <Radio.Group aria-label="试算对象" value={target}
      onChange={(event) => onTargetChange(event.target.value as TrialTarget)}
      options={[{ label: '已保存草稿', value: 'DRAFT' },
        { label: '已发布版本', value: 'PUBLISHED' }]} />
    {target === 'PUBLISHED' && <div className="rule-trial-version">
      <label>规则版本 ID <InputNumber aria-label="试算发布版本 ID" min={1}
        value={versionId} onChange={onVersionIdChange} /></label>
      <Button size="small" disabled={!versionId || loading || busy}
        onClick={onLoadPublishedFields}>载入该版本的输入项</Button>
      <Typography.Text type="secondary">可填当前或历史发布版本 ID。</Typography.Text>
    </div>}
    {target === 'PUBLISHED' && versionId && !versionFieldsReady && !loading && <Typography.Paragraph
      type="warning" className="rule-trial-save-hint">
      请先载入该版本所需的输入项。
    </Typography.Paragraph>}
    {target === 'DRAFT' && draftDirty && <Typography.Paragraph type="warning" className="rule-trial-save-hint">
      当前草稿有未保存修改，请先保存草稿再试算。
    </Typography.Paragraph>}
    {!hasSavedDraft && <Typography.Paragraph type="secondary" className="rule-trial-save-hint">
      请先保存规则集草稿。
    </Typography.Paragraph>}
    <Button type="primary" className="rule-trial-run" loading={busy} disabled={!canRun}
      onClick={target === 'DRAFT' ? onRunDraft : onRunPublished}>开始试算</Button>

    {(result || error) && <div className="rule-trial-output">
      <div className="rule-trial-step"><span>3</span><strong>查看试算结果</strong></div>
      {error && <Alert type="error" showIcon title="试算未完成" description={error} />}
      {result && !error && <div className="rule-trial-result">
        <div className="rule-trial-result-top"><Tag color="blue">{result.versionLabel}</Tag>
          <Tag color={result.evaluation.matchedRuleId ? 'green' : 'gold'}>
            {result.evaluation.matchedRuleId
              ? `命中规则行：${result.evaluation.matchedRuleId}` : '所有规则都未命中'}
          </Tag></div>
        <div className="rule-trial-result-code">{result.evaluation.resultCode}</div>
        <div className="rule-trial-result-reason">{result.evaluation.reason}
          <span>原因码：{result.evaluation.reasonCode}</span></div>
        <Typography.Text type="secondary">这是规则集结果；完整流程的最终业务结果由流程决定。</Typography.Text>
      </div>}
    </div>}
  </Card>
}
