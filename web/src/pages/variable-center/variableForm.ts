import type { CreateVariableInput, VariableCategoryCode, VariableInput, VariableSourceType,
  VariableValueType } from '../../services/variableCenter'

export type VariableFormValue = {
  variableCode: string; variableName: string; valueType: VariableValueType
  sourceType: VariableSourceType; url: string; timeoutMs: number
  parameters: string; resultPath: string
  categoryCode?: VariableCategoryCode
}

export function toCreateVariableInput(value: VariableFormValue & { categoryCode: VariableCategoryCode },
  dependencyVersionIds: Record<string, number> = {}): CreateVariableInput {
  return { ...toVariableInput(value, dependencyVersionIds), categoryCode: value.categoryCode }
}

/** 仅外部来源保留连接配置；请求和流程上下文变量只声明名称与类型。 */
export function toVariableInput(value: VariableFormValue,
  dependencyVersionIds: Record<string, number> = {}): VariableInput {
  const external = value.sourceType === 'HTTP_GET' || value.sourceType === 'DEMO_MARKETING'
  return {
    variableCode: value.variableCode, variableName: value.variableName, valueType: value.valueType,
    sourceType: value.sourceType, url: external ? value.url : '',
    timeoutMs: external ? value.timeoutMs : 1000,
    parameterNames: external ? value.parameters.split(/[\s,，]+/).filter(Boolean) : [],
    dependencyVersionIds: external ? dependencyVersionIds : {},
    resultPath: external ? value.resultPath : '',
  }
}
