import type { FlowNode, GraphDefinition } from './types'

const code = 'demoCheckoutDecision'
const expectedIds = ['start', 'eligibilityCall', 'selectionCall', 'highCheck', 'highResult',
  'lowCheck', 'lowResult', 'noResult', 'lowJoin', 'highJoin', 'end']

/** 仅为未保存坐标的固定营销外层图提供可读的分支位置，不覆盖用户保存的布局。 */
export function marketingDiagramLayout(flowCode: string, graph: GraphDefinition): GraphDefinition['layout'] | null {
  if (flowCode !== code || Object.keys(graph.layout).length > 0
    || graph.nodes.length !== expectedIds.length
    || expectedIds.some((id) => !graph.nodes.some((node) => node.id === id))) return null

  return {
    start: { x: 350, y: 10 },
    eligibilityCall: { x: 350, y: 90 },
    selectionCall: { x: 350, y: 170 },
    highCheck: { x: 350, y: 250 },
    highResult: { x: 650, y: 340 },
    lowCheck: { x: 120, y: 340 },
    lowResult: { x: 20, y: 430 },
    noResult: { x: 230, y: 430 },
    lowJoin: { x: 120, y: 520 },
    highJoin: { x: 350, y: 610 },
    end: { x: 350, y: 700 },
  }
}

/** 将演示图的技术节点名换成业务可读标题；配置仍在节点检查器中查看。 */
export function marketingNodeCaption(flowCode: string, node: FlowNode): string | null {
  if (flowCode !== code) return null
  if (node.kind === 'SUBFLOW') {
    const name = node.config.flowCode === 'demoOfferEligibility' ? '优惠资格'
      : node.config.flowCode === 'demoOfferSelection' ? '优惠选择' : '决策子流程'
    return `${name} · #${node.config.versionId ?? '?'}`
  }
  if (node.kind === 'CONDITION') {
    if (node.config.expected === 'HIGH_VALUE_OFFER') return '建议高额券？'
    if (node.config.expected === 'LOW_VALUE_OFFER') return '建议低额券？'
  }
  if (node.kind === 'RESULT') {
    const details = node.config.details
    const offerCode = details && typeof details === 'object' && 'offerCode' in details
      ? details.offerCode : null
    if (node.config.conclusion === 'HIGH_VALUE_OFFER') {
      return offerCode === 'HIGH_18_OFF_100' ? '推荐 18 元券' : '推荐高额券'
    }
    if (node.config.conclusion === 'LOW_VALUE_OFFER') {
      return offerCode === 'LOW_5_OFF_100' ? '推荐 5 元券' : '推荐低额券'
    }
    if (node.config.conclusion === 'NO_NEW_OFFER') return '不新增优惠'
  }
  if (node.kind === 'CONDITION_JOIN') return node.id === 'lowJoin' ? '低额分支汇合' : '最终结果汇合'
  if (node.kind === 'START') return '开始'
  if (node.kind === 'END') return '结束'
  return null
}
