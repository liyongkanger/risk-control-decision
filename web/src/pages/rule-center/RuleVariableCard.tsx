import { Button, Input, Tag, Tooltip } from 'antd'
import { useEffect, useState } from 'react'
import type { RuleDefinition } from '../../services/ruleCenter'
import { getVariableVersion } from '../../services/variableCenter'

type RuleInput = RuleDefinition['inputs'][number]
type Props = { input: RuleInput; index: number; referenced: boolean;
  onAliasChange: (name: string) => void; onReplace: () => void; onRemove: () => void }

const typeNames = { STRING: '文本', BOOLEAN: '布尔', DECIMAL: '数值' }
const typeColors = { STRING: 'blue', BOOLEAN: 'green', DECIMAL: 'purple' }

/** 展示一条规则输入与变量中心固定版本的绑定关系。 */
export default function RuleVariableCard({ input, index, referenced,
  onAliasChange, onReplace, onRemove }: Props) {
  const [variableName, setVariableName] = useState<string | null>(null)
  useEffect(() => {
    let active = true
    setVariableName(null)
    void getVariableVersion(input.variableBinding.variableVersionId)
      .then((variable) => { if (active) setVariableName(variable.variableName) })
      .catch(() => { if (active) setVariableName(null) })
    return () => { active = false }
  }, [input.variableBinding.variableVersionId])

  return <div className="rule-variable-card">
    <div className="rule-variable-summary">
      <span className="rule-variable-index">{index + 1}</span>
      <div className="rule-variable-identity">
        <div className="rule-variable-title">
          <strong>{variableName || input.variableBinding.variableCode}</strong>
          <Tag color={typeColors[input.type]}>{typeNames[input.type]}</Tag>
        </div>
        <span className="rule-variable-version">{input.variableBinding.variableCode}
          {' · '}固定版本 ID {input.variableBinding.variableVersionId}</span>
      </div>
    </div>
    <label className="rule-variable-alias">规则输入名
      <Input aria-label={`规则输入名 ${index + 1}`} placeholder="填写规则内使用的名称"
        value={input.name} onChange={(event) => onAliasChange(event.target.value)} />
    </label>
    <div className="rule-variable-actions">
      <Button size="small" onClick={onReplace}>更换变量</Button>
      <Tooltip title={referenced ? '已有规则条件使用这个输入，请先移除相关条件' : undefined}>
        <span><Button size="small" type="text" danger disabled={referenced}
          onClick={onRemove}>移除</Button></span>
      </Tooltip>
    </div>
  </div>
}
