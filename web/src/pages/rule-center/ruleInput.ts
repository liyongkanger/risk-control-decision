import type { RuleDefinition } from '../../services/ruleCenter'

/** 浏览器无法精确表示小数，试算时要求小数以十进制文本输入。 */
export function parseRuleTestInput(raw: string, definition: RuleDefinition): Record<string, unknown> {
  const parsed: unknown = JSON.parse(raw)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('试算输入必须是 JSON 对象')
  }
  const input = parsed as Record<string, unknown>
  for (const field of definition.inputs) {
    const value = input[field.name]
    if (field.type === 'DECIMAL' && typeof value === 'number' && !Number.isSafeInteger(value)) {
      throw new Error(`DECIMAL 输入 ${field.name} 请用字符串表示精确小数`)
    }
  }
  return input
}
