import { Button, Card, Table, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'
import type { DemoCheckout, DemoGroup, DemoReport } from '../../services/marketingDemo'
import { describeConclusion, describeOffer, groupNames } from './demoPresentation'
import DecisionChainDrawer from './DecisionChainDrawer'

type Props = { report: DemoReport | null; loading: boolean; selectedId: string | null
  onSelect: (record: DemoCheckout) => void }
/** 逐笔对照推荐与实际结果，并提供规则及流程版本追溯。 */
export default function DecisionRecordsPanel({ report, loading, selectedId, onSelect }: Props) {
  const [detailId, setDetailId] = useState<string | null>(null)
  useEffect(() => { setDetailId(null) }, [report?.experimentId])
  const detail = report?.attempts.find((item) => item.checkoutId === detailId) ?? null
  const selectForFeedback = (item: DemoCheckout) => {
    onSelect(item)
    setDetailId(null)
    window.requestAnimationFrame(() => document.getElementById('marketing-feedback')?.scrollIntoView({ behavior: 'smooth' }))
  }
  return <Card title={`4. 逐笔核对决策与实际结果（${report?.attempts.length ?? 0}）`} loading={loading}>
    <Typography.Paragraph type="secondary">每笔记录都能核对建议、实际用券、利润，以及当时固定的规则与流程版本。</Typography.Paragraph>
    <Table<DemoCheckout> rowKey="checkoutId" dataSource={report?.attempts ?? []} scroll={{ x: 1080 }}
      pagination={{ pageSize: 10 }} rowClassName={(item) => item.checkoutId === selectedId ? 'ant-table-row-selected' : ''}
      columns={[
        { title: '结算 ID', dataIndex: 'checkoutId' },
        { title: '用户', dataIndex: 'userId' },
        { title: '组别', dataIndex: 'group', render: (value: DemoGroup) => groupNames[value] },
        { title: '决策建议', dataIndex: 'decisionConclusion', render: (_: string | null, item: DemoCheckout) =>
          item.decisionStatus === 'FAILED' ? <Tag color="red">执行失败</Tag> : describeConclusion(item) },
        { title: '推荐券', dataIndex: 'recommendedOfferCode', render: describeOffer },
        { title: '实际券', dataIndex: 'actualOfferCode', render: describeOffer },
        { title: '状态', render: (_: unknown, item: DemoCheckout) => item.refunded ? '已退款'
          : !item.settled ? '待回传' : item.paid ? '已支付' : '未支付' },
        { title: '贡献利润（元）', dataIndex: 'contributionProfit', render: (value: number) => Number(value).toFixed(2) },
        { title: '操作', render: (_: unknown, item: DemoCheckout) => <>
          <Button type="link" onClick={() => { onSelect(item); setDetailId(item.checkoutId) }}>查看决策链</Button>
        </> },
      ]} />
    <DecisionChainDrawer attempt={detail} onClose={() => setDetailId(null)} onFeedback={selectForFeedback} />
  </Card>
}
