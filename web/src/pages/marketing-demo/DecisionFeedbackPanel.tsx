import { Alert, Button, Card, Form, Input, InputNumber, Select, Space, Tag, Typography } from 'antd'
import { useEffect, useState } from 'react'
import type { CheckoutInput, DemoCheckout, FeedbackInput } from '../../services/marketingDemo'
import { describeConclusion, describeOffer, groupNames } from './demoPresentation'

type Props = { experimentId: string; selected: DemoCheckout | null; busy: boolean; ready: boolean
  onCheckout: (input: CheckoutInput) => void
  onFeedback: (input: FeedbackInput) => void }
type CheckoutFields = Omit<CheckoutInput, 'experimentId'>

/** 在同一笔决策上下文中完成试决策、支付反馈与退款。 */
export default function DecisionFeedbackPanel({ experimentId, selected, busy, ready, onCheckout, onFeedback }: Props) {
  const [checkoutForm] = Form.useForm<CheckoutFields>()
  const [eventId, setEventId] = useState('')
  const [paid, setPaid] = useState(true)
  const [offerCode, setOfferCode] = useState('NONE')

  useEffect(() => {
    setPaid(true)
    setOfferCode(selected?.recommendedOfferCode ?? 'NONE')
    setEventId(selected ? `${selected.checkoutId}${selected.settled ? 'Refunded' : 'Paid'}` : '')
  }, [selected?.checkoutId, selected?.settled])

  const offerOptions = [{ value: 'NONE', label: '不使用优惠' }]
  if (selected && selected.existingOfferAmount > 0) offerOptions.push({ value: 'EXISTING', label: '使用已有优惠' })
  if (selected && selected.recommendedOfferCode !== 'NONE') offerOptions.push({
    value: selected.recommendedOfferCode, label: `使用推荐券 ${selected.recommendedOfferCode}`,
  })
  const canSettle = selected?.decisionStatus === 'SUCCEEDED' && !selected.settled
  const canRefund = selected?.decisionStatus === 'SUCCEEDED' && selected.settled
    && selected.paid && !selected.refunded

  return <Space orientation="vertical" size="large" style={{ width: '100%' }}>
    <Card title="2. 体验一笔结算决策">
      <Typography.Paragraph type="secondary">填写一笔模拟结算，查看所属实验组、资格判断与优惠建议。金额会登记为模拟订单事实，再由变量中心供规则读取。</Typography.Paragraph>
      <Form form={checkoutForm} layout="inline" initialValues={{ orderAmount: 120, preDiscountMargin: 30, existingOfferAmount: 0 }}
        onFinish={(values) => onCheckout({ ...values, experimentId })} style={{ rowGap: 12 }}>
        <Form.Item name="checkoutId" label="结算 ID" rules={[{ required: true }]}><Input placeholder="order001" /></Form.Item>
        <Form.Item name="userId" label="用户 ID" rules={[{ required: true }]}><Input placeholder="user001" /></Form.Item>
        <Form.Item name="orderAmount" label="订单金额" rules={[{ required: true }]}><InputNumber min={0} precision={2} /></Form.Item>
        <Form.Item name="preDiscountMargin" label="优惠前毛利" rules={[{ required: true }]}><InputNumber min={0} precision={2} /></Form.Item>
        <Form.Item name="existingOfferAmount" label="已有优惠" rules={[{ required: true }]}><InputNumber min={0} precision={2} /></Form.Item>
        <Form.Item><Button htmlType="submit" loading={busy} disabled={!ready}>执行单笔决策</Button></Form.Item>
      </Form>
      {selected && <Alert style={{ marginTop: 16 }} showIcon type={selected.decisionStatus === 'SUCCEEDED' ? 'info' : 'error'}
        title={`${selected.checkoutId} · ${groupNames[selected.group]} · ${describeConclusion(selected)}`}
        description={<Space orientation="vertical" size={2}>
          <span>{selected.decisionReason ?? '请查看逐笔记录'}；推荐优惠：{describeOffer(selected.recommendedOfferCode)}</span>
          <span>规则原因码：{selected.ruleReasonCode ?? '无'}。完整规则命中和版本链可在下方逐笔记录查看。</span>
        </Space>} />}
    </Card>
    <Card title="3. 回传实际支付结果" id="marketing-feedback">
      {!selected ? <Typography.Text type="secondary">先执行单笔决策，或在逐笔记录中选择一笔。</Typography.Text> : <>
        <Typography.Paragraph className="marketing-feedback-current">当前记录：<Tag>{selected.checkoutId}</Tag>
          建议 <strong>{describeOffer(selected.recommendedOfferCode)}</strong> · 实际使用 <strong>{describeOffer(selected.actualOfferCode)}</strong>
          · 当前贡献利润 <strong>{Number(selected.contributionProfit).toFixed(2)} 元</strong></Typography.Paragraph>
        <Typography.Paragraph type="secondary">推荐优惠只是决策建议；这里回传的实际支付与用券决定利润。已支付订单可继续回传全额退款。</Typography.Paragraph>
        {selected.decisionStatus !== 'SUCCEEDED' && <Alert type="error" showIcon title="决策运行失败，不能提交正常结算反馈" />}
        {(canSettle || canRefund) && <Space wrap>
          <label>事件 ID <Input aria-label="反馈事件 ID" value={eventId} style={{ width: 180 }}
            onChange={(event) => setEventId(event.target.value)} /></label>
          {canSettle && <>
            <label>是否支付 <Select aria-label="是否支付" value={paid} style={{ width: 110 }}
              options={[{ value: true, label: '已支付' }, { value: false, label: '未支付' }]}
              onChange={(value: boolean) => { setPaid(value); if (!value) setOfferCode('NONE') }} /></label>
            <label>实际优惠 <Select aria-label="实际优惠" value={paid ? offerCode : 'NONE'} disabled={!paid}
              style={{ minWidth: 210 }} options={offerOptions} onChange={setOfferCode} /></label>
            <Button type="primary" disabled={!eventId} loading={busy} onClick={() => onFeedback({
              experimentId, checkoutId: selected.checkoutId, eventId, eventType: 'SETTLED',
              paid, appliedOfferCode: paid ? offerCode : 'NONE',
            })}>回传结算</Button>
          </>}
          {canRefund && <Button danger disabled={!eventId} loading={busy} onClick={() => onFeedback({
            experimentId, checkoutId: selected.checkoutId, eventId, eventType: 'REFUNDED',
          })}>回传全额退款</Button>}
        </Space>}
        {selected.settled && !canRefund && selected.decisionStatus === 'SUCCEEDED' && <Typography.Text type="secondary">
          {selected.refunded ? '该笔已全额退款。' : selected.paid ? '该笔已支付。' : '该笔未支付。'}
        </Typography.Text>}
      </>}
    </Card>
  </Space>
}
