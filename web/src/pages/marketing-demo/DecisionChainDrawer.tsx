import { Button, Drawer, Tag, Typography } from 'antd'
import { history } from '@umijs/max'
import type { ReactNode } from 'react'
import type { DemoCheckout } from '../../services/marketingDemo'
import { describeConclusion, describeOffer, describeProfit, describeRuleMatch,
  groupNames } from './demoPresentation'

type Props = { attempt: DemoCheckout | null; onClose: () => void; onFeedback: (item: DemoCheckout) => void }

function ChainStep({ number, title, badge, children }: {
  number: string; title: string; badge?: ReactNode; children: ReactNode }) {
  return <section className="marketing-chain-step">
    <div className="marketing-chain-marker">{number}</div>
    <div className="marketing-chain-content">
      <div className="marketing-chain-step-title"><strong>{title}</strong>{badge}</div>
      {children}
    </div>
  </section>
}

function TechnicalFields({ children }: { children: ReactNode }) {
  return <details className="marketing-chain-technical"><summary>查看技术字段与固定版本</summary>{children}</details>
}

/** 按本次可追溯的业务事实与固定版本，展示决策到反馈的完整阅读路径。 */
export default function DecisionChainDrawer({ attempt, onClose, onFeedback }: Props) {
  const succeeded = attempt?.decisionStatus === 'SUCCEEDED'
  return <Drawer title="本次决策链" open={attempt !== null} onClose={onClose} size={680}>
    {attempt && <>
      <div className="marketing-chain-summary">
        <div><Tag color={succeeded ? 'blue' : 'red'}>{succeeded ? '决策完成' : '决策执行失败'}</Tag>
          <Tag>{groupNames[attempt.group]}</Tag></div>
        <strong>{describeConclusion(attempt)}</strong>
        <span>结算 {attempt.checkoutId} · 用户 {attempt.userId} · 实验 {attempt.experimentId}</span>
      </div>
      <Typography.Paragraph type="secondary" className="marketing-chain-intro">
        沿本次记录追溯输入、资格、优惠选择和最终建议。下方实际反馈发生在决策之后；记录仅提供接口返回的结果与版本，未包含每个流程节点的原始执行日志。
      </Typography.Paragraph>
      {attempt.executionId && <Button style={{ marginBottom: 18 }} onClick={() => history.push(
        `/decision-records?executionId=${encodeURIComponent(attempt.executionId ?? '')}`)}>
        查看平台原始执行记录</Button>}
      <div className="marketing-chain-timeline">
        <ChainStep number="1" title="模拟结算进入实验" badge={<Tag color="geekblue">输入</Tag>}>
          <p>用户按实验 ID 稳定分组，本次进入 <strong>{groupNames[attempt.group]}</strong>。</p>
          <div className="marketing-chain-values">
            <span>订单金额 <strong>{Number(attempt.orderAmount).toFixed(2)} 元</strong></span>
            <span>优惠前毛利 <strong>{Number(attempt.preDiscountMargin).toFixed(2)} 元</strong></span>
            <span>已有优惠 <strong>{Number(attempt.existingOfferAmount).toFixed(2)} 元</strong></span>
          </div>
          <TechnicalFields><p>请求定位字段：uid={attempt.userId}，checkoutId={attempt.checkoutId}，experimentId={attempt.experimentId}。</p>
            <p>金额先登记为模拟结算事实，规则通过变量中心按定位字段读取。</p></TechnicalFields>
        </ChainStep>

        <ChainStep number="2" title="资格子流程判断" badge={<Tag color={attempt.eligible ? 'green' : 'orange'}>
          {succeeded ? attempt.eligible ? '符合资格' : '不符合资格' : '未完成'}</Tag>}>
          <p>{attempt.ruleSetCode ? describeRuleMatch(attempt) : '本次没有可追溯的资格规则结果。'}</p>
          <TechnicalFields>
            <p>资格流程固定版本：#{attempt.subflowVersionIds.eligibilityCall ?? '无记录'}</p>
            <p>规则集：{attempt.ruleSetCode ?? '无记录'} · 固定版本：#{attempt.ruleVersionId ?? '无记录'}</p>
            <p>命中行：{attempt.matchedRuleId ?? (attempt.ruleSetCode && attempt.ruleReasonCode
              ? '所有规则行未命中' : '无记录')} · 原因码：{attempt.ruleReasonCode ?? '无记录'}</p>
          </TechnicalFields>
        </ChainStep>

        <ChainStep number="3" title="优惠选择子流程" badge={<Tag color="purple">{groupNames[attempt.group]}</Tag>}>
          <p>{succeeded ? <>根据资格结果和实验组，本次<strong>{attempt.recommendedOfferCode === 'NONE'
            ? '不新增优惠' : `推荐 ${describeOffer(attempt.recommendedOfferCode)}`}</strong>。</>
            : '决策未完成，无法确认本次优惠选择结果。'}</p>
          <TechnicalFields><p>优惠选择流程固定版本：#{attempt.subflowVersionIds.selectionCall ?? '无记录'}</p>
            <p>推荐券码：{attempt.recommendedOfferCode}</p></TechnicalFields>
        </ChainStep>

        <ChainStep number="4" title="外层流程形成业务结论" badge={<Tag color={succeeded ? 'blue' : 'red'}>
          {succeeded ? '决策结果' : '执行失败'}</Tag>}>
          <p><strong>{describeConclusion(attempt)}</strong> · {attempt.decisionReason ?? '无原因说明'}</p>
          <TechnicalFields><p>外层流程固定版本：#{attempt.outerVersionId}</p>
            <p>结论码：{attempt.decisionConclusion ?? '无'} · 执行状态：{attempt.decisionStatus}</p></TechnicalFields>
        </ChainStep>
      </div>

      <div className="marketing-chain-feedback">
        <div className="marketing-chain-feedback-heading"><span>决策之后</span><strong>实际反馈与利润</strong></div>
        <p>建议优惠不等于实际用券。{attempt.settled && <><strong>{describeOffer(attempt.actualOfferCode)}</strong> 为本次实际使用；</>}
          {attempt.refunded ? '订单已全额退款。' : !attempt.settled ? '结算结果尚未回传。'
            : attempt.paid ? '订单已支付。' : '订单未支付。'}</p>
        <div className="marketing-chain-profit">
          <span>贡献利润</span><strong>{Number(attempt.contributionProfit).toFixed(2)} 元</strong>
          <small>{describeProfit(attempt)}</small>
        </div>
        {!attempt.settled && succeeded && <Button type="primary" onClick={() => onFeedback(attempt)}>
          回传这笔实际结果</Button>}
      </div>
    </>}
  </Drawer>
}
