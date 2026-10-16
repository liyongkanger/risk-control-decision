import type { RuleDefinition } from '../../services/ruleCenter'

type VariableChoice = { id: number; variableCode: string; valueType: 'STRING' | 'BOOLEAN' | 'DECIMAL' }

/** 判断规则条件是否仍引用指定输入名。 */
export function isRuleInputReferenced(definition: RuleDefinition, inputName: string): boolean {
  return definition.rows.some((row) => row.conditions.some((condition) => condition.input === inputName))
}

/** 将变量中心的固定版本加入规则输入。 */
export function selectRuleVariable(definition: RuleDefinition, variable: VariableChoice,
  index?: number): RuleDefinition {
  const selected = { name: index === undefined ? variable.variableCode
    : definition.inputs[index]?.name || variable.variableCode,
  type: variable.valueType,
  variableBinding: { variableCode: variable.variableCode, variableVersionId: variable.id } }
  if (index === undefined) {
    if (definition.inputs.some((input) => input.name === selected.name)) {
      throw new Error(`规则输入名已存在：${selected.name}`)
    }
    return { ...definition, inputs: [...definition.inputs, selected] }
  }
  const previous = definition.inputs[index]
  if (!previous) throw new Error('规则输入不存在')
  const rows = previous.type === variable.valueType ? definition.rows
    : definition.rows.map((row) => ({ ...row,
      conditions: row.conditions.map((condition) => condition.input === previous.name
        ? { ...condition, operator: 'EQ' as const,
          expected: null } : condition),
    }))
  return { ...definition, inputs: definition.inputs.map((input, i) => i === index ? selected : input), rows }
}

/** 修改规则输入别名及其条件引用。 */
export function renameRuleInput(definition: RuleDefinition, index: number, name: string): RuleDefinition {
  const oldName = definition.inputs[index]?.name
  if (oldName === undefined) return definition
  if (name !== oldName && definition.inputs.some((input, i) => i !== index && input.name === name)) {
    throw new Error(`规则输入名已存在：${name}`)
  }
  return { ...definition,
    inputs: definition.inputs.map((input, i) => i === index ? { ...input, name } : input),
    rows: definition.rows.map((row) => ({ ...row,
      conditions: row.conditions.map((condition) => condition.input === oldName
        ? { ...condition, input: name } : condition),
    })),
  }
}
