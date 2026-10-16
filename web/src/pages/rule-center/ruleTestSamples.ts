import type { RuleDefinition } from '../../services/ruleCenter'
import type { VariableVersion } from '../../services/variableCenter'

type Samples = { input: Record<string, unknown>; contextValues: Record<string, unknown> }
export type RuleTrialField = { code: string; source: 'REQUEST_INPUT' | 'FLOW_CONTEXT';
  valueType: VariableVersion['valueType'] }

/** 表单修改单个值时保留其余字段，十进制文本不转成 JavaScript 数字。 */
export function setRuleTrialField(raw: string, code: string, value: unknown): string {
  const parsed: unknown = JSON.parse(raw)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('试算输入必须是 JSON 对象')
  }
  return JSON.stringify({ ...parsed, [code]: value }, null, 2)
}

/** 重新生成样例时保留用户已填写的同名字段。 */
export function retainRuleTestValues(defaults: Samples, previous: Samples): Samples {
  const retain = (values: Record<string, unknown>, prior: Record<string, unknown>) =>
    Object.fromEntries(Object.entries(values).map(([key, value]) =>
      [key, Object.prototype.hasOwnProperty.call(prior, key) ? prior[key] : value]))
  return { input: retain(defaults.input, previous.input),
    contextValues: retain(defaults.contextValues, previous.contextValues) }
}

/** 根据固定变量版本生成规则试算所需的请求和上下文样例。 */
export async function buildRuleTestSamples(definition: RuleDefinition,
  loadVersion: (id: number) => Promise<VariableVersion>): Promise<Samples> {
  return (await buildRuleTrialSetup(definition, loadVersion)).samples
}

/** 根据固定变量及其依赖生成试算表单字段和初始样例。 */
export async function buildRuleTrialSetup(definition: RuleDefinition,
  loadVersion: (id: number) => Promise<VariableVersion>): Promise<{
    samples: Samples; fields: RuleTrialField[] }> {
  const samples: Samples = { input: {}, contextValues: {} }
  const fields: RuleTrialField[] = []
  const visited = new Set<number>()
  const versions = new Map<number, VariableVersion>()
  const sampleValue = (type: VariableVersion['valueType']) =>
    type === 'DECIMAL' ? '0' : type === 'BOOLEAN' ? false : ''
  const visit = async (id: number, expectedCode: string): Promise<void> => {
    if (visited.has(id)) return
    const version = versions.get(id) ?? await loadVersion(id)
    versions.set(id, version)
    if (version.variableCode !== expectedCode) {
      throw new Error(`变量版本与变量码不匹配：${expectedCode}`)
    }
    visited.add(id)
    if (version.sourceType === 'REQUEST_INPUT') {
      samples.input[version.variableCode] = sampleValue(version.valueType)
      fields.push({ code: version.variableCode, source: 'REQUEST_INPUT', valueType: version.valueType })
    } else if (version.sourceType === 'FLOW_CONTEXT') {
      samples.contextValues[version.variableCode] = sampleValue(version.valueType)
      fields.push({ code: version.variableCode, source: 'FLOW_CONTEXT', valueType: version.valueType })
    } else {
      for (const [code, dependencyId] of Object.entries(version.dependencyVersionIds ?? {})) {
        await visit(dependencyId, code)
      }
    }
  }
  for (const input of definition.inputs) {
    await visit(input.variableBinding.variableVersionId, input.variableBinding.variableCode)
  }
  return { samples, fields }
}
