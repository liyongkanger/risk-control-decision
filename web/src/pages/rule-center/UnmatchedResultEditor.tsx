import { Card, Input, Select, Typography } from 'antd'
import type { RuleOutcome } from '../../services/ruleCenter'

type Props = { value: RuleOutcome; resultCodes: string[]; ruleCount: number
  onChange: (value: RuleOutcome) => void }

/** 编辑所有规则行均未命中时返回的正常业务结果。 */
export default function UnmatchedResultEditor({ value, resultCodes, ruleCount, onChange }: Props) {
  return <Card size="small" className="rule-unmatched-card"
    title={<span>所有规则都未命中时</span>}>
    <Typography.Paragraph type="secondary" className="rule-unmatched-description">
      上方规则按优先级逐条判断；如果没有一条规则满足条件，就返回这里配置的业务结果。
    </Typography.Paragraph>
    <div className="rule-unmatched-path" aria-label="未命中时的返回路径">
      <span>{ruleCount === 0 ? '尚无规则行' : `检查 ${ruleCount} 条规则`}</span>
      <span aria-hidden="true">→</span>
      <strong>全部未命中</strong><span aria-hidden="true">→</span><span>返回下方结果</span>
    </div>
    <div className="rule-unmatched-result">
      <label>业务结果码
        <Select aria-label="未命中结果码" value={value.resultCode}
          style={{ width: '100%' }} options={resultCodes.map((code) => ({ value: code, label: code }))}
          onChange={(resultCode) => onChange({ ...value, resultCode })} />
      </label>
      <label>原因码
        <Input aria-label="未命中原因码" placeholder="如 QUALIFIED" value={value.reasonCode}
          onChange={(event) => onChange({ ...value, reasonCode: event.target.value })} />
      </label>
      <label className="rule-unmatched-reason">原因说明
        <Input aria-label="未命中原因说明" placeholder="说明为什么返回此结果" value={value.reason}
          onChange={(event) => onChange({ ...value, reason: event.target.value })} />
      </label>
    </div>
    <Typography.Text type="secondary" className="rule-unmatched-note">
      这是正常业务结果。变量取值失败或流程执行失败会直接报错，不会使用这里的结果。
    </Typography.Text>
  </Card>
}
