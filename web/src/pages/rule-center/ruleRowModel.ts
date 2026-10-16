import type { RuleDefinition } from '../../services/ruleCenter'

type RuleRow = RuleDefinition['rows'][number]
type RuleInput = RuleDefinition['inputs'][number]

const conditionFor = (input: RuleInput): RuleRow['conditions'][number] => ({
  input: input.name, operator: 'EQ', expected: null,
})

/** 为新规则行生成编辑草稿。 */
export function createDraftRuleRow(definition: RuleDefinition): RuleRow {
  const first = definition.inputs[0]
  if (!first) throw new Error('请先从变量中心选择变量')
  const ids = new Set(definition.rows.map((row) => row.id))
  let number = 1
  while (ids.has(`rule-${number}`)) number++
  return { id: `rule-${number}`,
    priority: Math.max(0, ...definition.rows.map((row) => row.priority)) + 10,
    conditions: [conditionFor(first)],
    result: { resultCode: definition.resultCodes[0] ?? '', reasonCode: '', reason: '' },
  }
}

/** 在规则行末尾添加一个 AND 条件。 */
export function addAndCondition(row: RuleRow, inputs: RuleInput[]): RuleRow {
  const first = inputs[0]
  if (!first) throw new Error('请先从变量中心选择变量')
  return { ...row, conditions: [...row.conditions, conditionFor(first)] }
}

/** 切换条件引用的规则输入。 */
export function changeConditionField(row: RuleRow, index: number, inputName: string,
  inputs: RuleInput[]): RuleRow {
  const selected = inputs.find((input) => input.name === inputName)
  if (!selected) throw new Error(`规则输入不存在：${inputName}`)
  return { ...row, conditions: row.conditions.map((condition, i) => {
    if (i !== index) return condition
    const previousType = inputs.find((input) => input.name === condition.input)?.type
    return previousType === selected.type ? { ...condition, input: inputName }
      : conditionFor(selected)
  }) }
}
