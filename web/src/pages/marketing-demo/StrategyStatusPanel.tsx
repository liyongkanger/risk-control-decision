import { Alert, Button, Card, Space, Table, Tag, Typography } from 'antd'
import type { DemoStatus } from '../../services/marketingDemo'
import { pendingStrategySteps } from './strategyState'

type Props = { status: DemoStatus | null; loading: boolean; busy: boolean
  onRefresh: () => void; onActivate: () => void }

const showVersion = (value: number | null | undefined) => value == null ? '未建立' : `#${value}`

/** 展示当前资产与场景运行链，按依赖顺序引导下一版。 */
export default function StrategyStatusPanel({ status, loading, busy, onRefresh, onActivate }: Props) {
  const pending = status ? pendingStrategySteps(status) : []
  const rows = status ? [
    { key: 'rule', name: '资格规则集', current: status.ruleCurrentVersionId,
      running: status.runningRuleVersionId, href: '/rule-center?keyword=demoOfferEligibilityRules' },
    { key: 'eligibility', name: '资格子流程', current: status.eligibilityCurrentVersionId,
      running: status.runningEligibilityVersionId, href: '/flows/demoOfferEligibility/editor' },
    { key: 'selection', name: '优惠选择子流程', current: status.selectionCurrentVersionId,
      running: status.runningSelectionVersionId, href: '/flows/demoOfferSelection/editor' },
    { key: 'outer', name: '外层结算流程', current: status.outerCurrentVersionId,
      running: status.sceneSelectedOuterVersionId, href: '/flows/demoCheckoutDecision/editor' },
  ] : []
  const canActivate = status && (!status.initialized
    || status.outerCurrentVersionId !== status.sceneSelectedOuterVersionId)

  return <Card title={status?.initialized ? '5. 查看规则与流程版本' : '准备演示决策链'} loading={loading} extra={<Space>
    <Button onClick={onRefresh} loading={loading}>刷新策略状态</Button>
    {canActivate && <Button type="primary" loading={busy} onClick={onActivate}>
      {status?.initialized ? '选用当前外层版本' : '初始化样板'}
    </Button>}
  </Space>}>
    {!status && <Typography.Text type="secondary">正在读取策略状态。</Typography.Text>}
    {status && <>
      <Alert type={status.initialized ? 'info' : 'warning'} showIcon style={{ marginBottom: 16 }}
        title={status.initialized ? '当前运行链可执行' : '样板尚未形成完整运行链'}
        description={status.initialized
          ? '下表展示每笔新决策会用到的固定版本；发布新规则或流程后，需完成下方待处理步骤才会切换。'
          : '先初始化样板，再运行固定样本或执行单笔决策。'} />
      <Table rowKey="key" size="small" pagination={false} dataSource={rows}
        columns={[{ title: '资产', dataIndex: 'name', render: (value: string, row: typeof rows[number]) =>
          <a href={row.href} target="_blank" rel="noreferrer">{value}</a> },
        { title: '资产当前发布', dataIndex: 'current', render: showVersion },
        { title: '场景实际运行', dataIndex: 'running', render: showVersion },
        { title: '状态', render: (_: unknown, row: typeof rows[number]) => row.current == null || row.running == null
          ? <Tag>未就绪</Tag> : row.current === row.running
            ? <Tag color="green">已应用</Tag> : <Tag color="orange">有新版待应用</Tag> }]} />
      <Typography.Title level={5} style={{ marginTop: 20 }}>下一版待处理</Typography.Title>
      {pending.length === 0 ? <Typography.Text type="success">当前发布资产与运行链已对齐。</Typography.Text>
        : <Space orientation="vertical">{pending.map((step, index) =>
          <Typography.Text key={step.kind}>{index + 1}. {step.href
            ? <a href={step.href} target="_blank" rel="noreferrer">{step.message}</a> : step.message}</Typography.Text>)}</Space>}
    </>}
  </Card>
}
