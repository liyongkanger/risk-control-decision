import { Button, Card, Empty, Space, Statistic, Tag, Typography } from 'antd'
import type { DemoGroup, DemoMetrics, DemoReport } from '../../services/marketingDemo'
import { groupNames, reportHeadline } from './demoPresentation'

type Props = { report: DemoReport | null; loading: boolean; busy: boolean; ready: boolean;
  onFixture: () => void }
const groups: DemoGroup[] = ['CONTROL', 'LOW', 'HIGH']
const amount = (value: number | null | undefined) => value == null ? '样本不足' : Number(value).toFixed(2)
const percentage = (value: number | null | undefined) => value == null ? '样本不足' : `${Number(value).toFixed(2)}%`

function groupCard(group: DemoGroup, metric: DemoMetrics | undefined) {
  const delta = metric?.incrementalAverageContributionProfit
  return <Card key={group} title={groupNames[group]} size="small" style={{ minWidth: 260, flex: 1 }}>
    <Statistic title="人均贡献利润（元）" value={metric && metric.users > 0
      ? metric.averageContributionProfit : '—'} precision={metric && metric.users > 0 ? 2 : undefined} />
    <Typography.Paragraph style={{ margin: '8px 0' }}>相对对照组：{group === 'CONTROL' ? '基线'
      : delta == null ? '样本不足' : <Tag color={delta >= 0 ? 'green' : 'red'}>
        {delta > 0 ? '+' : ''}{amount(delta)} 元/人</Tag>}</Typography.Paragraph>
    <Space orientation="vertical" size={2}>
      <Typography.Text>合格用户 {metric?.users ?? 0} · 支付转化 {percentage(metric && metric.users > 0 ? metric.paymentConversionRate : null)}</Typography.Text>
      <Typography.Text>实际商家优惠 {amount(metric?.merchantDiscount)} 元</Typography.Text>
      <Typography.Text>退款率 {percentage(metric?.refundRate)} · 推荐券实际使用率 {percentage(metric?.recommendedOfferUseRate)}</Typography.Text>
    </Space>
  </Card>
}

/** 按对照组基线展示模拟实验的人均利润差值与实际反馈指标。 */
export default function ExperimentReportPanel({ report, loading, busy, ready, onFixture }: Props) {
  const headline = reportHeadline(report)
  return <Card title="1. 看实验结果" loading={loading}>
    <div className="marketing-report-intro"><div>
      <strong>先比较实际反馈，再决定是否调整优惠策略</strong>
      <p>未支付用户计入合格用户分母；退款后利润为零。样本结果不代表真实营销收益。</p>
    </div><Button type="primary" disabled={!ready || busy} loading={busy} onClick={onFixture}>
      运行 30 人固定样本</Button></div>
    {report && report.attempts.length > 0 && <div className="marketing-report-summary">
      <strong>{headline.title}</strong><div>{headline.detail}</div>
    </div>}
    {!report ? <Empty description="还没有加载实验报告" /> : <>
      <Space align="start" size="middle" wrap style={{ width: '100%' }}>
        {groups.map((group) => groupCard(group, report.groups[group]))}
      </Space>
      {report.attempts.length === 30 && report.attempts.every((item) => item.checkoutId.startsWith('fixture-')) &&
        <Typography.Paragraph type="secondary" style={{ marginTop: 16 }}>
        本实验包含固定 30 人样本。支付行为由演示数据预设；规则与流程照常执行。
        </Typography.Paragraph>}
    </>}
  </Card>
}
