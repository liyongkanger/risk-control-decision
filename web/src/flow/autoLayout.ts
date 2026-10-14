import type { FlowEdge, GraphDefinition } from './types'

const columnGap = 230
const rowGap = 110

/** 按拓扑层级和分支相对位置排列有向无环图，仅返回节点坐标。 */
export function arrangeGraph(graph: GraphDefinition): GraphDefinition['layout'] {
  const ids = graph.nodes.map((node) => node.id)
  if (new Set(ids).size !== ids.length) throw new Error('节点 ID 重复，无法一键整理')
  const known = new Set(ids)
  const nodeOrder = new Map(ids.map((id, index) => [id, index]))
  const outgoing = new Map(ids.map((id) => [id, [] as FlowEdge[]]))
  const indegree = new Map(ids.map((id) => [id, 0]))
  for (const edge of graph.edges) {
    if (!known.has(edge.source) || !known.has(edge.target)) continue
    outgoing.get(edge.source)!.push(edge)
    indegree.set(edge.target, indegree.get(edge.target)! + 1)
  }

  const queue = ids.filter((id) => indegree.get(id) === 0)
  const ranks = new Map(ids.map((id) => [id, 0]))
  const lanes = new Map<string, number>()
  const laneProposals = new Map<string, number[]>()
  let visited = 0
  while (queue.length > 0) {
    const id = queue.shift()!
    visited++
    const proposals = laneProposals.get(id) ?? []
    const lane = proposals.length === 0 ? 0
      : proposals.reduce((sum, value) => sum + value, 0) / proposals.length
    lanes.set(id, lane)
    const edges = outgoing.get(id)!.slice().sort((a, b) => {
      const branchOrder = (edge: FlowEdge) => edge.branch === 'true' ? -1
        : edge.branch === 'false' ? 1 : 0
      return branchOrder(a) - branchOrder(b)
    })
    edges.forEach((edge, index) => {
      const offset = edges.length > 1 ? (index - (edges.length - 1) / 2) * 2 : 0
      laneProposals.set(edge.target, [...(laneProposals.get(edge.target) ?? []), lane + offset])
      ranks.set(edge.target, Math.max(ranks.get(edge.target)!, ranks.get(id)! + 1))
      indegree.set(edge.target, indegree.get(edge.target)! - 1)
      if (indegree.get(edge.target) === 0) queue.push(edge.target)
    })
    queue.sort((a, b) => nodeOrder.get(a)! - nodeOrder.get(b)!)
  }
  if (visited !== ids.length) throw new Error('图中存在环路，无法一键整理')

  const layers = new Map<number, string[]>()
  for (const id of ids) layers.set(ranks.get(id)!, [...(layers.get(ranks.get(id)!) ?? []), id])
  const raw = new Map<string, { x: number; y: number }>()
  for (const [rank, layer] of layers) {
    const sorted = layer.slice().sort((a, b) => lanes.get(a)! - lanes.get(b)!
      || nodeOrder.get(a)! - nodeOrder.get(b)!)
    const desired = sorted.map((id) => lanes.get(id)! * columnGap)
    const spaced: number[] = []
    desired.forEach((x, index) => spaced.push(index === 0 ? x : Math.max(x, spaced[index - 1] + columnGap)))
    const correction = (desired.reduce((sum, x) => sum + x, 0)
      - spaced.reduce((sum, x) => sum + x, 0)) / sorted.length
    sorted.forEach((id, index) => raw.set(id, {
      x: spaced[index] + correction, y: 40 + rank * rowGap,
    }))
  }
  const minX = Math.min(0, ...Array.from(raw.values(), ({ x }) => x))
  return Object.fromEntries(ids.map((id) => {
    const position = raw.get(id)!
    return [id, { x: Math.round(position.x - minX + 80), y: position.y }]
  }))
}

/** 载入已保存布局时恢复上下方向的连接点。 */
export function isVerticalLayout(graph: GraphDefinition): boolean {
  let down = 0
  let right = 0
  for (const edge of graph.edges) {
    const source = graph.layout[edge.source]
    const target = graph.layout[edge.target]
    if (!source || !target) continue
    if (target.y > source.y) down++
    if (target.x > source.x) right++
  }
  return down > right && down > 0
}
