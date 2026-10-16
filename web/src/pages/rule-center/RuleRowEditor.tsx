import { Button, Input, InputNumber, Popconfirm, Select } from 'antd'
import type { RuleCondition, RuleDefinition } from '../../services/ruleCenter'
import { addAndCondition, changeConditionField } from './ruleRowModel'

type RuleRow = RuleDefinition['rows'][number]
type Props = { row: RuleRow; index: number; inputs: RuleDefinition['inputs']; resultCodes: string[]
  onChange: (row: RuleRow) => void; onRemove: () => void }

const operators = [
  { value: 'EQ', label: '等于 ＝' }, { value: 'NE', label: '不等于 ≠' },
  { value: 'GT', label: '大于 ＞' }, { value: 'GTE', label: '大于等于 ≥' },
  { value: 'LT', label: '小于 ＜' }, { value: 'LTE', label: '小于等于 ≤' },
] as const

/** 逐条编辑首命中规则的条件与返回结果，字段结构沿用规则接口契约。 */
export default function RuleRowEditor({ row, index, inputs, resultCodes, onChange, onRemove }: Props) {
  const number = index + 1
  const updateCondition = (conditionIndex: number, patch: Partial<RuleCondition>) => {
    onChange({ ...row, conditions: row.conditions.map((condition, i) =>
      i === conditionIndex ? { ...condition, ...patch } : condition) })
  }
  const removeCondition = (conditionIndex: number) => onChange({ ...row,
    conditions: row.conditions.filter((_, i) => i !== conditionIndex) })

  return <div className="rule-row-editor">
    <div className="rule-row-heading">
      <div className="rule-row-heading-title"><span className="rule-row-number">规则 {number}</span>
        <span className="rule-row-heading-note">按优先级从小到大匹配，命中后停止</span></div>
      <Popconfirm title="删除这条规则？" description="仅从当前草稿移除这条规则行。"
        okText="删除" cancelText="取消" onConfirm={onRemove}>
        <Button type="text" danger>删除规则</Button>
      </Popconfirm>
    </div>
    <div className="rule-row-meta">
      <label>规则标识 <Input aria-label={`规则行标识 ${number}`} placeholder="如 order-threshold"
        value={row.id} onChange={(event) => onChange({ ...row, id: event.target.value })} /></label>
      <label>优先级 <InputNumber aria-label={`优先级 ${number}`} min={0} precision={0}
        style={{ width: '100%' }} value={row.priority}
        onChange={(value) => onChange({ ...row, priority: value ?? 0 })} /></label>
    </div>

    <section className="rule-row-section rule-row-when">
      <div className="rule-row-section-title"><span className="rule-row-section-mark">当</span>
        <strong>同时满足以下条件</strong><span className="rule-row-section-note">多个条件按 AND 判断</span></div>
      {row.conditions.map((condition, conditionIndex) => {
        const type = inputs.find((input) => input.name === condition.input)?.type ?? 'STRING'
        const label = `${number}-${conditionIndex + 1}`
        return <div key={conditionIndex}>
          {conditionIndex > 0 && <div className="rule-row-and"><span>且</span></div>}
          <div className="rule-row-condition">
            <label>变量 / 输入 <Select aria-label={`条件字段 ${label}`} value={condition.input}
              style={{ width: '100%' }} options={inputs.map((input) => ({
                value: input.name, label: `${input.name} · ${input.variableBinding.variableCode}`,
              }))} onChange={(value) => onChange(changeConditionField(row, conditionIndex, value, inputs))} /></label>
            <label>比较关系 <Select aria-label={`比较符 ${label}`} value={condition.operator}
              style={{ width: '100%' }} options={operators.filter((item) =>
                type === 'DECIMAL' || item.value === 'EQ' || item.value === 'NE')}
              onChange={(value: RuleCondition['operator']) => updateCondition(conditionIndex, { operator: value })} /></label>
            <label>比较值 {type === 'BOOLEAN' ? <Select aria-label={`预期值 ${label}`}
              placeholder="请选择 true 或 false" value={condition.expected === true ? 'true'
                : condition.expected === false ? 'false' : undefined}
              style={{ width: '100%' }} options={[{ value: 'true', label: 'true' },
                { value: 'false', label: 'false' }]}
              onChange={(value) => updateCondition(conditionIndex, { expected: value === 'true' })} />
              : <Input aria-label={`预期值 ${label}`} placeholder={type === 'DECIMAL'
                ? '输入精确十进制数，如 100.05' : '输入要比较的文本'}
                value={condition.expected == null ? '' : String(condition.expected)}
                onChange={(event) => updateCondition(conditionIndex, { expected: event.target.value })} />}</label>
            <Button type="text" danger className="rule-row-remove-condition"
              disabled={row.conditions.length <= 1} onClick={() => removeCondition(conditionIndex)}>移除</Button>
          </div>
        </div>
      })}
      <Button type="dashed" size="small" onClick={() => onChange(addAndCondition(row, inputs))}>
        + 添加且条件
      </Button>
    </section>

    <section className="rule-row-section rule-row-then">
      <div className="rule-row-section-title"><span className="rule-row-section-mark">则</span>
        <strong>命中后返回</strong></div>
      <div className="rule-row-outcome">
        <label>结果码 <Select aria-label={`结果码 ${number}`} value={row.result.resultCode}
          style={{ width: '100%' }} options={resultCodes.map((value) => ({ value, label: value }))}
          onChange={(value) => onChange({ ...row, result: { ...row.result, resultCode: value } })} /></label>
        <label>原因码 <Input aria-label={`原因码 ${number}`} placeholder="如 ORDER_LOW"
          value={row.result.reasonCode}
          onChange={(event) => onChange({ ...row, result: {
            ...row.result, reasonCode: event.target.value } })} /></label>
        <label className="rule-row-reason">原因说明 <Input aria-label={`原因 ${number}`}
          placeholder="说明为什么返回这个结果" value={row.result.reason}
          onChange={(event) => onChange({ ...row, result: {
            ...row.result, reason: event.target.value } })} /></label>
      </div>
    </section>
  </div>
}
