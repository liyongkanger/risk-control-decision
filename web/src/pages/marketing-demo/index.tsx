import { PageContainer } from '@ant-design/pro-components'
import { Alert, Button, Card, Form, Input, InputNumber, Select, Space, Statistic, Table, Tag, Typography, message } from 'antd'
import { useEffect, useState } from 'react'
import { checkoutMarketingDemo, feedbackMarketingDemo, getMarketingReport, runMarketingFixture, setupMarketingDemo } from '../../services/marketingDemo'
import type { CheckoutInput, DemoCheckout, DemoGroup, DemoReport, DemoSetup } from '../../services/marketingDemo'

const groupNames: Record<DemoGroup, string> = { CONTROL: '对照组', LOW: '低额券组', HIGH: '高额券组' }
const groupOrder: DemoGroup[] = ['CONTROL', 'LOW', 'HIGH']

/** 固定结算优惠场景的可运行演示与逐笔反馈。 */
export default function MarketingDemoPage() {
  const [notice, noticeHolder] = message.useMessage()
  const [experimentId, setExperimentId] = useState('demoCheckout')
  const [setup, setSetup] = useState<DemoSetup | null>(null)
  const [report, setReport] = useState<DemoReport | null>(null)
  const [latest, setLatest] = useState<DemoCheckout | null>(null)
  const [busy, setBusy] = useState(false)
  const [checkoutForm] = Form.useForm<CheckoutInput>()
  const [feedbackForm] = Form.useForm<{ checkoutId: string; eventId: string; eventType: 'SETTLED' | 'REFUNDED'; paid: boolean; appliedOfferCode: string }>()

  const perform = async (action: () => Promise<void>) => {
    setBusy(true)
    try { await action() }
    catch (error) { notice.error(error instanceof Error ? error.message : '操作失败') }
    finally { setBusy(false) }
  }

  const refresh = async () => setReport(await getMarketingReport(experimentId))
  useEffect(() => { void perform(refresh) }, [])

  const runFixture = () => perform(async () => {
    const active = setup ?? await setupMarketingDemo()
    setSetup(active)
    setReport(await runMarketingFixture(experimentId))
    notice.success('固定样本已运行')
  })

  const checkout = () => perform(async () => {
    const values = await checkoutForm.validateFields()
    const attempt = await checkoutMarketingDemo({ ...values, experimentId })
    setLatest(attempt)
    feedbackForm.setFieldsValue({ checkoutId: attempt.checkoutId, eventId: `${attempt.checkoutId}Paid`,
      eventType: 'SETTLED', paid: true, appliedOfferCode: attempt.recommendedOfferCode })
    await refresh()
    notice.success('模拟结算决策已执行')
  })

  const feedback = () => perform(async () => {
    const values = await feedbackForm.validateFields()
    setLatest(await feedbackMarketingDemo({ ...values, experimentId,
      ...(values.eventType === 'REFUNDED' ? { paid: undefined, appliedOfferCode: undefined } : {}) }))
    await refresh()
    notice.success('实际反馈已记录')
  })

  const columns = [
    { title: '结算 ID', dataIndex: 'checkoutId', key: 'checkoutId' },
    { title: '用户', dataIndex: 'userId', key: 'userId' },
    { title: '分组', dataIndex: 'group', key: 'group', render: (group: DemoGroup) => groupNames[group] },
    { title: '结论', dataIndex: 'decisionConclusion', key: 'decisionConclusion', render: (value: string | null) => value ?? '执行失败' },
    { title: '推荐券', dataIndex: 'recommendedOfferCode', key: 'recommendedOfferCode' },
    { title: '实际券', dataIndex: 'actualOfferCode', key: 'actualOfferCode' },
    { title: '状态', key: 'state', render: (_: unknown, item: DemoCheckout) => item.refunded ? <Tag>已退款</Tag>
      : item.settled ? <Tag color={item.paid ? 'green' : 'default'}>{item.paid ? '已支付' : '未支付'}</Tag> : <Tag color="blue">待回传</Tag> },
    { title: '贡献利润（元）', dataIndex: 'contributionProfit', key: 'contributionProfit', render: (value: number) => Number(value).toFixed(2) },
  ]

  return <PageContainer title="结算优惠营销闭环演示">
    {noticeHolder}
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Alert type="info" showIcon title="模拟数据演示" description="流程发布与决策执行使用真实平台能力；订单、支付、退款和实验样本仅保存在当前服务进程中。" />
      <Card title="1. 初始化与运行固定样本">
        <Space wrap>
          <Typography.Text>实验 ID</Typography.Text>
          <Input aria-label="实验 ID" value={experimentId} onChange={(event) => setExperimentId(event.target.value)} style={{ width: 250 }} />
          <Button loading={busy} onClick={() => void perform(async () => { setSetup(await setupMarketingDemo()); notice.success('演示场景已初始化') })}>初始化流程</Button>
          <Button type="primary" loading={busy} onClick={() => void runFixture()}>运行 30 人固定样本</Button>
          <Button loading={busy} onClick={() => void perform(refresh)}>刷新报告</Button>
        </Space>
        {setup && <Typography.Paragraph style={{ marginTop: 12 }}>场景 {setup.sceneCode} · 外层版本 #{setup.outerVersionId} · 资格子流程 #{setup.eligibilityVersionId} · 优惠子流程 #{setup.selectionVersionId}</Typography.Paragraph>}
      </Card>
      <Card title="2. 观察分组效果">
        <Space size="large" wrap>
          {groupOrder.map((group) => {
            const metric = report?.groups[group]
            return <Card key={group} title={groupNames[group]} size="small" style={{ minWidth: 245 }}>
              <Statistic title="人均贡献利润（元）" value={metric?.averageContributionProfit ?? 0} precision={2} />
              <Typography.Text>支付转化率 {Number(metric?.paymentConversionRate ?? 0).toFixed(2)}% · 用户 {metric?.users ?? 0} · 实际商家折扣 {Number(metric?.merchantDiscount ?? 0).toFixed(2)} 元</Typography.Text>
            </Card>
          })}
        </Space>
        <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>样板预设：对照组 20% / 6.00 元，低额券组 30% / 7.50 元，高额券组 40% / 4.80 元。以人均贡献利润观察低额券组最优。</Typography.Paragraph>
      </Card>
      <Card title="3. 手动模拟结算与实际回传">
        <Form form={checkoutForm} layout="inline" initialValues={{ orderAmount: 120, preDiscountMargin: 30, existingOfferAmount: 0 }} onFinish={() => void checkout()} style={{ rowGap: 12 }}>
          <Form.Item name="checkoutId" label="结算 ID" rules={[{ required: true }]}><Input placeholder="order001" /></Form.Item>
          <Form.Item name="userId" label="用户 ID" rules={[{ required: true }]}><Input placeholder="user001" /></Form.Item>
          <Form.Item name="orderAmount" label="订单金额" rules={[{ required: true }]}><InputNumber min={0} precision={2} /></Form.Item>
          <Form.Item name="preDiscountMargin" label="优惠前毛利" rules={[{ required: true }]}><InputNumber min={0} precision={2} /></Form.Item>
          <Form.Item name="existingOfferAmount" label="已有优惠" rules={[{ required: true }]}><InputNumber min={0} precision={2} /></Form.Item>
          <Form.Item><Button htmlType="submit" loading={busy}>执行决策</Button></Form.Item>
        </Form>
        {latest && <Alert type="success" style={{ margin: '16px 0' }} title={`最近一次：${latest.checkoutId} · ${groupNames[latest.group]} · ${latest.decisionConclusion ?? latest.decisionStatus} · 推荐 ${latest.recommendedOfferCode} · 实际 ${latest.actualOfferCode} · 利润 ${Number(latest.contributionProfit).toFixed(2)} 元`} />}
        <Form form={feedbackForm} layout="inline" onFinish={() => void feedback()} style={{ rowGap: 12 }}>
          <Form.Item name="checkoutId" label="结算 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="eventId" label="事件 ID" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="eventType" label="反馈类型" initialValue="SETTLED"><Select style={{ width: 120 }} options={[{ value: 'SETTLED', label: '支付结算' }, { value: 'REFUNDED', label: '全额退款' }]} /></Form.Item>
          <Form.Item name="paid" label="已支付" initialValue={true}><Select style={{ width: 90 }} options={[{ value: true, label: '是' }, { value: false, label: '否' }]} /></Form.Item>
          <Form.Item name="appliedOfferCode" label="实际使用优惠" initialValue="NONE"><Input placeholder="NONE / EXISTING / 推荐券码" style={{ width: 210 }} /></Form.Item>
          <Form.Item><Button htmlType="submit" loading={busy}>回传反馈</Button></Form.Item>
        </Form>
      </Card>
      <Card title={`逐笔记录（${report?.attempts.length ?? 0}）`}><Table rowKey="checkoutId" dataSource={report?.attempts ?? []} columns={columns} scroll={{ x: 1100 }} pagination={{ pageSize: 10 }} /></Card>
    </Space>
  </PageContainer>
}
