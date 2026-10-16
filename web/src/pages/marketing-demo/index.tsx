import { PageContainer } from '@ant-design/pro-components'
import { Alert, Button, Card, Input, Space, Tag, Typography, message } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { checkoutMarketingDemo, feedbackMarketingDemo, getMarketingDemoStatus,
  getMarketingReport, runMarketingFixture, setupMarketingDemo } from '../../services/marketingDemo'
import type { CheckoutInput, DemoCheckout, DemoReport, DemoStatus, FeedbackInput } from '../../services/marketingDemo'
import DecisionFeedbackPanel from './DecisionFeedbackPanel'
import DecisionRecordsPanel from './DecisionRecordsPanel'
import ExperimentReportPanel from './ExperimentReportPanel'
import StrategyStatusPanel from './StrategyStatusPanel'
import { pendingStrategySteps } from './strategyState'
import { reportHeadline } from './demoPresentation'
import './marketing-demo.css'

/** 固定营销样板的版本准备、试决策、反馈和增量复盘工作台。 */
export default function MarketingDemoPage() {
  const [notice, holder] = message.useMessage()
  const [experimentInput, setExperimentInput] = useState('demoCheckout')
  const [experimentId, setExperimentId] = useState('demoCheckout')
  const [status, setStatus] = useState<DemoStatus | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)
  const [statusLoading, setStatusLoading] = useState(false)
  const [report, setReport] = useState<DemoReport | null>(null)
  const [reportLoading, setReportLoading] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const reportRequest = useRef(0)
  const statusRequest = useRef(0)
  const selected = report?.attempts.find((item) => item.checkoutId === selectedId) ?? null

  const refreshStatus = async () => {
    const request = ++statusRequest.current
    setStatusLoading(true)
    try {
      const value = await getMarketingDemoStatus()
      if (request === statusRequest.current) { setStatus(value); setStatusError(null) }
      return value
    } catch (error) {
      const detail = error instanceof Error ? error.message : '策略状态读取失败'
      if (request === statusRequest.current) { setStatusError(detail); notice.error(detail) }
      return null
    } finally { if (request === statusRequest.current) setStatusLoading(false) }
  }
  const refreshReport = async (forExperiment = experimentId) => {
    const request = ++reportRequest.current
    setReportLoading(true)
    try {
      const value = await getMarketingReport(forExperiment)
      if (request === reportRequest.current) setReport(value)
    } catch (error) {
      if (request === reportRequest.current) notice.error(error instanceof Error ? error.message : '报告读取失败')
    } finally { if (request === reportRequest.current) setReportLoading(false) }
  }
  useEffect(() => { void refreshStatus(); return () => { statusRequest.current += 1 } }, [])
  useEffect(() => {
    setReport(null); setSelectedId(null)
    void refreshReport(experimentId)
    return () => { reportRequest.current += 1 }
  }, [experimentId])

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    try { await action() }
    catch (error) { notice.error(error instanceof Error ? error.message : '操作失败') }
    finally { setBusy(false) }
  }
  const changeExperiment = () => {
    const next = experimentInput.trim()
    if (!/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(next)) {
      notice.error('实验 ID 须以字母开头，最长 64 位，只能包含字母、数字、下划线和连字符')
      return
    }
    if (next === experimentId) void refreshReport(next)
    else setExperimentId(next)
  }
  const activate = () => void run(async () => {
    await setupMarketingDemo()
    const current = await refreshStatus()
    if (current?.initialized) notice.success('场景已选用外层当前发布版本')
    else if (current) notice.warning('样板尚未形成完整运行链，请检查顶部版本状态')
  })
  const fixture = () => {
    if ((report?.attempts.filter((item) => item.checkoutId.startsWith('fixture-')).length ?? 0) >= 30) {
      notice.info('当前实验已有固定样本，重复运行不会重新决策；比较新版本请先切换实验 ID')
      return
    }
    void run(async () => {
      const request = ++reportRequest.current
      setReportLoading(true)
      try {
        const value = await runMarketingFixture(experimentId)
        if (request === reportRequest.current) {
          setReport(value)
          notice.success('固定 30 人样本已运行')
        }
      } finally { if (request === reportRequest.current) setReportLoading(false) }
    })
  }
  const checkout = (input: CheckoutInput) => void run(async () => {
    const attempt = await checkoutMarketingDemo(input)
    setSelectedId(attempt.checkoutId)
    await refreshReport(experimentId)
    if (attempt.decisionStatus === 'SUCCEEDED') notice.success('单笔决策已执行，请核对建议并回传实际结果')
    else notice.warning('决策执行失败，请查看记录原因；不能提交正常结算反馈')
  })
  const feedback = (input: FeedbackInput) => void run(async () => {
    const attempt = await feedbackMarketingDemo(input)
    setSelectedId(attempt.checkoutId)
    await refreshReport(experimentId)
    notice.success(input.eventType === 'REFUNDED' ? '退款已回传，利润已冲回' : '实际结算反馈已记录')
  })
  const pending = status ? pendingStrategySteps(status) : []
  const headline = reportHeadline(report)

  return <PageContainer title="结算优惠营销工作台">
    {holder}
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <div className="marketing-hero">
        <div className="marketing-hero-copy">
          <Tag color="blue">结算优惠 · 核心演示</Tag>
          <h2>{headline.title}</h2>
          <p>{headline.detail}</p>
          <div className="marketing-hero-status">
            <Tag color={status?.initialized ? 'green' : 'orange'}>
              {status?.initialized ? '决策链可运行' : '决策链待准备'}</Tag>
            <span>真实规则与流程执行；结算、支付和退款为本机模拟数据，服务重启后清空。</span>
          </div>
        </div>
        <div className="marketing-hero-steps" aria-label="营销闭环步骤">
          <div><strong>01</strong><span>决策建议</span><small>资格判断与优惠推荐</small></div>
          <div><strong>02</strong><span>实际反馈</span><small>支付、用券与退款</small></div>
          <div><strong>03</strong><span>利润复盘</span><small>对照三组人均贡献</small></div>
        </div>
      </div>
      <Card title="当前实验">
        <Space wrap><Typography.Text>实验 ID</Typography.Text>
          <Input.Search aria-label="实验 ID" value={experimentInput} disabled={busy}
            onChange={(event) => setExperimentInput(event.target.value)} onSearch={changeExperiment}
            enterButton="切换 / 刷新" style={{ width: 310 }} />
          <Typography.Text>正在查看 <strong>{experimentId}</strong></Typography.Text>
        </Space>
      </Card>
      {statusError && <Alert type="error" showIcon title="策略状态读取失败" description={statusError} />}
      {status && !status.initialized && <StrategyStatusPanel status={status} loading={statusLoading} busy={busy}
        onRefresh={() => void refreshStatus()} onActivate={activate} />}
      <ExperimentReportPanel report={report} loading={reportLoading} busy={busy}
        ready={status?.initialized === true} onFixture={fixture} />
      <DecisionFeedbackPanel experimentId={experimentId} selected={selected} busy={busy}
        ready={status?.initialized === true} onCheckout={checkout} onFeedback={feedback} />
      <DecisionRecordsPanel report={report} loading={reportLoading} selectedId={selectedId}
        onSelect={(item: DemoCheckout) => setSelectedId(item.checkoutId)} />
      {status?.initialized && <StrategyStatusPanel status={status} loading={statusLoading} busy={busy}
        onRefresh={() => void refreshStatus()} onActivate={activate} />}
      <Card title="6. 准备下一版">
        {pending.length === 0 ? <Typography.Paragraph>当前运行链已对齐。查看上方利润差值后，可在规则中心调整下一版资格规则。</Typography.Paragraph>
          : <Typography.Paragraph>按顶部策略卡的待处理顺序更新版本；现有结算继续使用场景选中的版本。</Typography.Paragraph>}
        <Space wrap>
          <a href="/rule-center?keyword=demoOfferEligibilityRules" target="_blank" rel="noreferrer">编辑资格规则</a>
          <a href="/flows/demoOfferEligibility/editor" target="_blank" rel="noreferrer">设计资格流程</a>
          <a href="/flows/demoOfferSelection/editor" target="_blank" rel="noreferrer">设计优惠选择流程</a>
          <a href="/flows/demoCheckoutDecision/editor" target="_blank" rel="noreferrer">设计外层流程</a>
          <Button onClick={() => void refreshStatus()}>返回后刷新策略状态</Button>
        </Space>
      </Card>
    </Space>
  </PageContainer>
}
