import { useEffect, useRef, useState } from 'react'
import { Graph } from '@antv/x6'
import { history } from '@umijs/max'
import { evaluateFlow, loadTargetDraft, publishTargetFlow, saveTargetDraft, validateTargetFlow } from './api'
import type { DecisionRunResult as RunResult, FlowTarget } from './api'
import { toGraphDefinition } from './graphAdapter'
import { componentOptionsFor } from './componentOptions'
import { allNodeFlow, demoFlow, emptyFlow } from './templates'
import { parseJsonObject, parseRequiredInputs } from './subflowConfig'
import { marketingDiagramLayout, marketingNodeCaption } from './marketingDiagram'
import { arrangeGraph, isVerticalLayout } from './autoLayout'
import SubflowNodeInspector from './SubflowNodeInspector'
import ResultNodeInspector from './ResultNodeInspector'
import VariableNodeInspector from './VariableNodeInspector'
import CompareVariableInspector from './CompareVariableInspector'
import RuleSetNodeInspector from './RuleSetNodeInspector'
import RuleResultInspector from './RuleResultInspector'
import DecisionRunResult from './DecisionRunResult'
import type { ContextBinding, FlowEdge, FlowNode, FlowValidation, GraphDefinition, NodeKind } from './types'
import { getScene } from '../services/scene'
import { getFlow } from '../services/flow'
import { listComponentDefinitions } from '../services/componentCatalog'
import type { ComponentDefinition } from '../services/componentCatalog'
import { listVariables } from '../services/variableCenter'
import type { VariableVersion } from '../services/variableCenter'
import './editor.css'

const paletteGroups: { title: string; note: string; items: NodeKind[] }[] = [
  { title: '主路径', note: '从开始进入，经过处理，最后返回业务结果并结束。',
    items: ['START', 'COMPONENT', 'RESULT', 'END'] },
  { title: '判断与复用', note: '条件分出“满足 / 不满足”两条线；子流程调用固定发布版本。',
    items: ['CONDITION', 'CONDITION_JOIN', 'SUBFLOW'] },
  { title: '并行处理', note: '同时运行多条分支，汇合后再继续。',
    items: ['PARALLEL_SPLIT', 'PARALLEL_JOIN'] },
]
const labels: Record<NodeKind, string> = {
  START: '开始', END: '结束', COMPONENT: '处理组件', PARALLEL_SPLIT: '并行拆分',
  PARALLEL_JOIN: '并行汇合', CONDITION: '条件', CONDITION_JOIN: '条件汇合',
  RESULT: '业务结果',
  SUBFLOW: '决策子流程',
}
const colors: Record<NodeKind, string> = {
  START: '#1677ff', END: '#667085', COMPONENT: '#16a34a', PARALLEL_SPLIT: '#9333ea',
  PARALLEL_JOIN: '#9333ea', CONDITION: '#d97706', CONDITION_JOIN: '#d97706',
  RESULT: '#0f766e',
  SUBFLOW: '#0891b2',
}
const marks: Record<NodeKind, string> = {
  START: '▶', END: '■', COMPONENT: '●', PARALLEL_SPLIT: '⇉', PARALLEL_JOIN: '⇇',
  CONDITION: '◇', CONDITION_JOIN: '◆', SUBFLOW: '↳', RESULT: '✓',
}
const kindHints: Record<NodeKind, string> = {
  START: '流程入口，每条流程只有一个。',
  END: '流程出口，每条流程只有一个；当前不负责计算业务决策结论。',
  COMPONENT: '选择已注册能力执行并保存结果；默认示例值仅用于演示。',
  PARALLEL_SPLIT: '同时启动所有后续分支。',
  PARALLEL_JOIN: '等待所有并行分支结束后继续；当前只负责结构汇合。',
  CONDITION: '根据条件组件的 true／false 结果选择一条分支。',
  CONDITION_JOIN: '把选中的条件分支接回主路径；当前只负责结构汇合。',
  RESULT: '明确返回业务结论和原因；同一执行路径只能产生一个结果。',
  SUBFLOW: '调用固定版本的已发布决策流程；配置需包含流程码、版本 ID 和输入映射。',
}
const branchLabel = (branch: FlowEdge['branch']) => branch === 'true' ? '满足' : branch === 'false' ? '不满足' : ''
const issueHints: Record<string, string> = {
  START_COUNT: '流程需要且只能有一个开始节点。',
  END_COUNT: '流程需要且只能有一个结束节点。',
  INVALID_DEGREE: '检查该节点的输入线和输出线数量。',
  CONDITION_BRANCHES: '条件节点的两条输出线须分别设为“满足”和“不满足”。',
  PARALLEL_BRANCHES: '并行分支连线不使用条件标签。',
  RESULT_MISSING: '每条可执行结束路径都需要业务结果节点。',
  RESULT_DUPLICATE: '同一执行路径只能经过一个业务结果节点。',
}

function nodeTypeLabel(node: FlowNode): string {
  if (node.componentKey === 'get-variable') return '获取变量'
  if (node.componentKey === 'compare-value') return '比较变量'
  if (node.componentKey === 'execute-rule-set') return '执行规则集'
  if (node.componentKey === 'rule-result-equals') return '规则结果判断'
  return labels[node.kind]
}

function nodeCaption(node: FlowNode): string {
  if (node.componentKey === 'get-variable' && typeof node.config.variableCode === 'string'
    && node.config.variableCode) return node.config.variableCode
  if (node.componentKey === 'compare-value' && node.config.expected !== undefined) {
    const symbols: Record<string, string> = { EQ: '=', NE: '≠', GT: '>', GTE: '≥', LT: '<', LTE: '≤' }
    const operator = String(node.config.operator ?? 'EQ')
    return `${symbols[operator] ?? operator} ${String(node.config.expected)}`
  }
  if (node.componentKey === 'execute-rule-set' && typeof node.config.ruleSetCode === 'string'
    && node.config.ruleSetCode) return node.config.ruleSetCode
  if (node.componentKey === 'rule-result-equals' && typeof node.config.expectedResultCode === 'string'
    && node.config.expectedResultCode) return `= ${node.config.expectedResultCode}`
  return node.id
}

function drawNode(graph: Graph, node: FlowNode, position: { x: number; y: number },
                  vertical = false, caption?: string) {
  graph.addNode({
    id: node.id, shape: 'rect', x: position.x, y: position.y,
    width: vertical ? 180 : 155, height: 60, data: node,
    markup: [
      { tagName: 'rect', selector: 'body' },
      { tagName: 'text', selector: 'kindTag' },
      { tagName: 'text', selector: 'label' },
    ],
    attrs: {
      body: { fill: '#fff', stroke: colors[node.kind], strokeWidth: 2, rx: 8, ry: 8 },
      kindTag: { text: `${marks[node.kind]}  ${nodeTypeLabel(node)}`, fill: colors[node.kind],
        refX: 12, refY: 0.24, textAnchor: 'start', textVerticalAnchor: 'middle',
        fontSize: 10, fontWeight: 700 },
      label: { text: caption ?? nodeCaption(node), fill: '#1f2937', refX: 0.5, refY: 0.67,
        textAnchor: 'middle', textVerticalAnchor: 'middle',
        fontSize: vertical ? 13 : 12, fontWeight: vertical ? 600 : 500 },
    },
    ports: {
      groups: {
        in: { position: vertical ? 'top' : 'left', attrs: { circle: { r: 7, magnet: true, strokeWidth: 2, stroke: colors[node.kind], fill: '#fff' } } },
        out: { position: vertical ? 'bottom' : 'right', attrs: { circle: { r: 7, magnet: true, strokeWidth: 2, stroke: colors[node.kind], fill: '#fff' } } },
      },
      items: [
        ...(node.kind === 'START' ? [] : [{ id: 'in', group: 'in' }]),
        ...(node.kind === 'END' ? [] : [{ id: 'out', group: 'out' }]),
      ],
    },
  })
}

function drawEdge(graph: Graph, edge: FlowEdge) {
  graph.addEdge({
    id: edge.id, source: { cell: edge.source, port: 'out' }, target: { cell: edge.target, port: 'in' },
    data: { branch: edge.branch }, labels: edge.branch ? [branchLabel(edge.branch)] : [],
    attrs: { line: { stroke: '#8292a8', strokeWidth: 2, targetMarker: 'classic' } },
  })
}

/** 同时服务场景入口与独立流程资产的 X6 流程设计器。 */
export default function FlowEditor({ target }: { target: FlowTarget }) {
  const code = target.code
  const isAsset = target.kind === 'flow'
  const canvasRef = useRef<HTMLDivElement>(null)
  const paletteRef = useRef<HTMLDetailsElement>(null)
  const graphRef = useRef<Graph | null>(null)
  const baselineSnapshot = useRef<string | null>(null)
  const requiredInputsRef = useRef<string[] | undefined>(undefined)
  const contextBindingsRef = useRef<ContextBinding[] | undefined>(undefined)
  const nextId = useRef(4)
  const [ownerName, setOwnerName] = useState('')
  const [ownerFlowCode, setOwnerFlowCode] = useState('')
  const [ownerError, setOwnerError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [selected, setSelected] = useState<{ id: string; type: 'node' | 'edge' } | null>(null)
  const [configText, setConfigText] = useState('{}')
  const [validation, setValidation] = useState<FlowValidation | null>(null)
  const [savedSnapshot, setSavedSnapshot] = useState<string | null>(null)
  const [isDirty, setIsDirty] = useState(false)
  const [validatedSnapshot, setValidatedSnapshot] = useState<string | null>(null)
  const [message, setMessage] = useState('从节点右侧连接点拖到下一节点左侧连接点。')
  const [nodeCount, setNodeCount] = useState(3)
  const [edgeCount, setEdgeCount] = useState(2)
  const [busy, setBusy] = useState(false)
  const [componentCatalog, setComponentCatalog] = useState<ComponentDefinition[] | undefined>()
  const [canvasHeight, setCanvasHeight] = useState<number | null>(null)
  const [requiredInputsText, setRequiredInputsText] = useState('')
  const [requiredInputsError, setRequiredInputsError] = useState<string | null>(null)
  const [contextBindings, setContextBindings] = useState<ContextBinding[]>([])
  const [contextVariables, setContextVariables] = useState<VariableVersion[]>([])
  const [runInputText, setRunInputText] = useState('{"positive": true}')
  const [runResult, setRunResult] = useState<RunResult | null>(null)
  const [, forceRender] = useState(0)

  const recalculateDirty = (current: Graph) => {
    setIsDirty(baselineSnapshot.current !== null
      && JSON.stringify(toGraphDefinition(current, requiredInputsRef.current, contextBindingsRef.current)) !== baselineSnapshot.current)
  }

  const refresh = (graph: Graph) => {
    setNodeCount(graph.getNodes().length)
    setEdgeCount(graph.getEdges().length)
    setValidation(null)
    setValidatedSnapshot(null)
    recalculateDirty(graph)
  }

  const render = (graph: Graph, definition: GraphDefinition) => {
    requiredInputsRef.current = definition.requiredInputs
    contextBindingsRef.current = definition.contextBindings
    setContextBindings(definition.contextBindings ?? [])
    setRequiredInputsText((definition.requiredInputs ?? []).join(', '))
    setRequiredInputsError(null)
    setRunResult(null)
    graph.resetCells([])
    const fallbackLayout = marketingDiagramLayout(code, definition)
    const marketingView = isAsset && code === 'demoCheckoutDecision'
    const positions = definition.nodes.map((node, index) => definition.layout[node.id]
      ?? fallbackLayout?.[node.id] ?? { x: 80 + index * 200, y: 180 })
    const visibleLayout = Object.fromEntries(definition.nodes.map((node, index) => [node.id, positions[index]]))
    const vertical = marketingView || isVerticalLayout({ ...definition, layout: visibleLayout })
    const height = vertical ? Math.min(1600, Math.max(800, ...positions.map(({ y }) => y + 100))) : null
    setCanvasHeight(height)
    if (canvasRef.current) {
      canvasRef.current.style.height = height === null ? '' : `${height}px`
      graph.resize(canvasRef.current.clientWidth, canvasRef.current.clientHeight)
    }
    definition.nodes.forEach((node, index) => drawNode(graph, node, positions[index],
      vertical, marketingNodeCaption(code, node) ?? undefined))
    definition.edges.forEach((edge) => drawEdge(graph, edge))
    graph.zoomToFit({ padding: 24, maxScale: 1 })
    graph.centerContent()
    nextId.current = definition.nodes.length + 1
    setSelected(null)
    refresh(graph)
  }

  useEffect(() => {
    let active = true
    void listComponentDefinitions().then((items) => {
      if (active) setComponentCatalog(items)
    }).catch((error) => { if (active) setMessage(error instanceof Error ? error.message : '组件目录读取失败') })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    void (async () => {
      const first = await listVariables('', 1, 100)
      const pages = await Promise.all(Array.from({ length: Math.ceil(first.total / 100) - 1 },
        (_, index) => listVariables('', index + 2, 100)))
      if (active) setContextVariables([first, ...pages].flatMap((page) => page.records)
        .filter((variable) => variable.sourceType === 'FLOW_CONTEXT'))
    })().catch((error) => { if (active) setMessage(error instanceof Error ? error.message : '上下文变量读取失败') })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!canvasRef.current) return
    const graph = new Graph({
      container: canvasRef.current, background: { color: '#f8fafc' }, grid: { size: 12, visible: true },
      panning: true, mousewheel: { enabled: true, modifiers: 'ctrl' },
      connecting: { allowBlank: false, allowLoop: false, allowNode: false, snap: true, router: 'orth', connector: 'rounded' },
    })
    graphRef.current = graph
    graph.on('cell:added', () => refresh(graph))
    graph.on('cell:removed', () => refresh(graph))
    graph.on('node:change:position', () => { setValidation(null); setValidatedSnapshot(null); recalculateDirty(graph) })
    graph.on('cell:click', ({ cell }) => {
      const type = cell.isNode() ? 'node' : 'edge'
      setSelected({ id: cell.id, type })
      setConfigText(type === 'node' ? JSON.stringify((cell.getData() as FlowNode)?.config ?? {}, null, 2) : '{}')
    })
    graph.on('edge:connected', ({ edge, isNew }) => {
      if (!isNew || !edge.getSourceCellId() || !edge.getTargetCellId()) return
      setSelected({ id: edge.id, type: 'edge' })
      const source = graph.getCellById(edge.getSourceCellId() ?? '')
      setMessage(source?.isNode() && (source.getData() as FlowNode).kind === 'CONDITION'
        ? '条件连线已创建：在右侧选择“满足”或“不满足”，两条分支各需一条。'
        : '连线已创建。右侧可核对来源与去向。')
    })
    render(graph, emptyFlow())
    baselineSnapshot.current = JSON.stringify(toGraphDefinition(graph, requiredInputsRef.current, contextBindingsRef.current))
    setIsDirty(false)
    const observer = new ResizeObserver(() => {
      if (canvasRef.current) graph.resize(canvasRef.current.clientWidth, canvasRef.current.clientHeight)
    })
    observer.observe(canvasRef.current)
    return () => { observer.disconnect(); graphRef.current = null; graph.dispose() }
  }, [])

  useEffect(() => {
    let active = true
    void (async () => {
      try {
        const owner = isAsset ? await getFlow(code) : await getScene(code)
        const name = isAsset ? ('flowName' in owner ? owner.flowName : '') : ('sceneName' in owner ? owner.sceneName : '')
        if (!active) return
        setOwnerName(name)
        setOwnerFlowCode(owner.flowCode)
        setOwnerError(null)
        const draft = await loadTargetDraft(target)
        if (!active || !graphRef.current) return
        render(graphRef.current, draft?.graph ?? emptyFlow())
        setRevision(draft?.revision ?? 0)
        const snapshot = JSON.stringify(toGraphDefinition(graphRef.current, requiredInputsRef.current, contextBindingsRef.current))
        baselineSnapshot.current = snapshot
        setIsDirty(false)
        setSavedSnapshot(draft ? snapshot : null)
        setMessage(draft ? `已加载草稿 r${draft.revision}` : '当前流程尚无草稿，请先设计流程。')
      } catch (error) {
        if (active) {
          const detail = error instanceof Error ? error.message : '流程加载失败'
          setOwnerError(detail.startsWith('Scene not found:') ? `场景不存在：${code}`
            : detail.startsWith('Flow not found:') ? `流程不存在：${code}` : detail)
        }
      }
    })()
    return () => { active = false }
  }, [target.kind, code])

  useEffect(() => {
    if (!isDirty) return
    const hasUnsavedChanges = () => graphRef.current && baselineSnapshot.current !== null
      && JSON.stringify(toGraphDefinition(graphRef.current, requiredInputsRef.current, contextBindingsRef.current)) !== baselineSnapshot.current
    const unblock = history.block((transition) => {
      if (hasUnsavedChanges() && !window.confirm('当前流程有未保存的修改，确定离开吗？')) return
      unblock()
      transition.retry()
    })
    return () => { unblock() }
  }, [isDirty])

  const graph = () => {
    if (!graphRef.current) throw new Error('画布尚未就绪')
    return graphRef.current
  }

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    try { await action() } catch (error) { setMessage(error instanceof Error ? error.message : String(error)) }
    finally { setBusy(false) }
  }

  const highlight = (result: FlowValidation) => {
    graph().getNodes().forEach((node) => node.attr('body/stroke', colors[(node.getData() as FlowNode).kind]))
    graph().getEdges().forEach((edge) => edge.attr('line/stroke', '#8292a8'))
    result.issues.forEach((issue) => {
      if (issue.nodeId) graph().getCellById(issue.nodeId)?.attr('body/stroke', '#e5484d')
      if (issue.edgeId) graph().getCellById(issue.edgeId)?.attr('line/stroke', '#e5484d')
    })
  }

  const addKind = (kind: NodeKind, componentKey?: string) => {
    if ((kind === 'START' || kind === 'END') && graph().getNodes().some((cell) =>
      (cell.getData() as FlowNode).kind === kind)) {
      setMessage(`${labels[kind]}节点每条流程只能有一个`)
      return
    }
    const prefix = kind.toLowerCase().replaceAll('_', '')
    let id = `${prefix}${nextId.current++}`
    while (graph().getCellById(id)) id = `${prefix}${nextId.current++}`
    const selectedKey = componentKey ?? (kind === 'COMPONENT' ? 'emit-value'
      : kind === 'CONDITION' ? 'is-positive' : null)
    const initialConfig = selectedKey
      ? componentCatalog?.find((item) => item.key === selectedKey)?.defaultConfig
      : undefined
    const vertical = (isAsset && code === 'demoCheckoutDecision')
      || isVerticalLayout(toGraphDefinition(graph(), requiredInputsRef.current, contextBindingsRef.current))
    const anchor = selected?.type === 'node' ? graph().getCellById(selected.id) : null
    const anchorPosition = anchor?.isNode() ? anchor.getPosition() : null
    const position = { x: anchorPosition ? anchorPosition.x + (vertical ? 0 : 210) : 160,
      y: anchorPosition ? anchorPosition.y + (vertical ? 120 : 0) : 120 }
    for (let attempt = 0; attempt < 40 && graph().getNodes().some((cell) => {
      const other = cell.getPosition()
      return Math.abs(other.x - position.x) < 170 && Math.abs(other.y - position.y) < 78
    }); attempt++) {
      if (vertical) position.x += 195
      else position.y += 95
    }
    drawNode(graph(), {
      id, kind, componentKey: selectedKey,
      failurePolicy: kind === 'COMPONENT' || kind === 'CONDITION' || kind === 'SUBFLOW' ? 'FAIL_FAST' : null,
      config: initialConfig ?? (kind === 'COMPONENT' ? { value: 'demo' }
        : kind === 'CONDITION' ? { inputKey: 'positive' } : {}),
    }, position, vertical)
    const added = graph().getCellById(id)
    if (added) graph().centerCell(added)
    setSelected({ id, type: 'node' })
    setConfigText(JSON.stringify(initialConfig ?? {}, null, 2))
    setMessage(`已添加“${componentKey ? nodeTypeLabel({ id, kind, componentKey, failurePolicy: null, config: {} }) : labels[kind]}”，在右侧完成配置，再从输出连接点拖到下一节点。`)
    if (paletteRef.current) paletteRef.current.open = false
    window.requestAnimationFrame(() => canvasRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
  }

  const updateNode = (changes: Partial<FlowNode>) => {
    if (!selected || selected.type !== 'node') return
    const cell = graph().getCellById(selected.id)
    if (!cell?.isNode()) return
    const next = { ...(cell.getData() as FlowNode), ...changes }
    cell.setData(next)
    cell.attr('label/text', marketingNodeCaption(code, next) ?? nodeCaption(next))
    cell.attr('kindTag/text', `${marks[next.kind]}  ${nodeTypeLabel(next)}`)
    cell.attr('kindTag/fill', colors[next.kind])
    setValidation(null)
    setValidatedSnapshot(null)
    forceRender((value) => value + 1)
    recalculateDirty(graph())
  }

  const updateBranch = (branch: FlowEdge['branch']) => {
    if (!selected || selected.type !== 'edge') return
    const cell = graph().getCellById(selected.id)
    if (!cell?.isEdge()) return
    cell.setData({ branch })
    cell.setLabels(branch ? [branchLabel(branch)] : [])
    setValidation(null)
    setValidatedSnapshot(null)
    forceRender((value) => value + 1)
    recalculateDirty(graph())
  }

  const changeRequiredInputs = (value: string) => {
    setRequiredInputsText(value)
    try {
      requiredInputsRef.current = parseRequiredInputs(value)
      setRequiredInputsError(null)
      setValidation(null)
      setValidatedSnapshot(null)
      if (graphRef.current) recalculateDirty(graphRef.current)
    } catch (error) {
      setRequiredInputsError(error instanceof Error ? error.message : '输入字段名无效')
    }
  }

  const changeContextBindings = (next: ContextBinding[]) => {
    contextBindingsRef.current = next
    setContextBindings(next)
    setValidation(null)
    setValidatedSnapshot(null)
    if (graphRef.current) recalculateDirty(graphRef.current)
  }

  const cell = selected ? graphRef.current?.getCellById(selected.id) : null
  const selectedNode = cell?.isNode() ? cell.getData() as FlowNode : null
  const selectedEdge = cell?.isEdge() ? cell.getData() as { branch: FlowEdge['branch'] } : null
  const selectedEdgeCell = cell?.isEdge() ? cell : null
  const edgeSourceId = selectedEdgeCell?.getSourceCellId() ?? ''
  const edgeTargetId = selectedEdgeCell?.getTargetCellId() ?? ''
  const sourceCell = edgeSourceId ? graphRef.current?.getCellById(edgeSourceId) : null
  const sourceIsCondition = sourceCell?.isNode() && (sourceCell.getData() as FlowNode).kind === 'CONDITION'
  const siblingBranches = graphRef.current?.getEdges().filter((edge) => edge.id !== selected?.id
    && edge.getSourceCellId() === edgeSourceId).map((edge) =>
    ((edge.getData() ?? {}) as { branch?: FlowEdge['branch'] }).branch) ?? []
  const selectedNodeEdges = selectedNode ? graphRef.current?.getEdges().filter((edge) =>
    edge.getSourceCellId() === selectedNode.id || edge.getTargetCellId() === selectedNode.id) ?? [] : []
  const selectedIncoming = selectedNodeEdges.filter((edge) => edge.getTargetCellId() === selectedNode?.id).length
  const selectedOutgoing = selectedNodeEdges.filter((edge) => edge.getSourceCellId() === selectedNode?.id).length
  const displayNode = (id: string) => {
    const nodeCell = graphRef.current?.getCellById(id)
    if (!nodeCell?.isNode()) return id || '未连接'
    const node = nodeCell.getData() as FlowNode
    const caption = marketingNodeCaption(code, node) ?? nodeCaption(node)
    return caption === id ? id : `${caption}（${id}）`
  }
  const focusCell = (id: string) => {
    const targetCell = graph().getCellById(id)
    if (!targetCell) return
    setSelected({ id, type: targetCell.isNode() ? 'node' : 'edge' })
    setConfigText(targetCell.isNode() ? JSON.stringify((targetCell.getData() as FlowNode).config, null, 2) : '{}')
    graph().centerCell(targetCell)
    canvasRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  if (ownerError) return <main className="flow-editor"><p role="alert">{ownerError}</p><button onClick={() => history.push(isAsset ? '/flows' : '/scenes')}>返回{isAsset ? '流程资产' : '场景管理'}</button></main>

  return <main className="flow-editor"><div className="page">
    <header className="header"><div><p className="eyebrow">RISK CONTROL DECISION</p><h1>{ownerName || (isAsset ? '流程' : '场景')} · 流程设计</h1><p className="subtitle">添加节点、连接路径、校验后发布</p></div>
      <div className="scene"><label htmlFor="ownerCode">{isAsset ? '流程码' : '场景码'}</label><input id="ownerCode" value={code} readOnly /></div></header>
    <section className="toolbar"><div className="actions">
      <button disabled={busy} onClick={() => run(async () => { const draft = await loadTargetDraft(target); if (!draft) { setMessage('该流程没有草稿'); return }; if (baselineSnapshot.current !== JSON.stringify(toGraphDefinition(graph(), requiredInputsRef.current, contextBindingsRef.current)) && !window.confirm('当前流程有未保存的修改，确定加载草稿吗？')) return; render(graph(), draft.graph); setRevision(draft.revision); const snapshot = JSON.stringify(toGraphDefinition(graph(), requiredInputsRef.current, contextBindingsRef.current)); baselineSnapshot.current = snapshot; setIsDirty(false); setSavedSnapshot(snapshot); setMessage(`已加载草稿 r${draft.revision}`) })}>加载草稿</button>
      <button className="primary-action" disabled={busy || !!requiredInputsError} onClick={() => run(async () => { const current = toGraphDefinition(graph(), requiredInputsRef.current, contextBindingsRef.current); const snapshot = JSON.stringify(current); const saved = await saveTargetDraft(target, current, revision); setRevision(saved.revision); setSavedSnapshot(snapshot); baselineSnapshot.current = snapshot; setIsDirty(false); if (validatedSnapshot !== snapshot) setValidation(null); setMessage(`已保存草稿 r${saved.revision}`) })}>保存草稿</button>
      <button disabled={busy} onClick={() => { if (baselineSnapshot.current !== JSON.stringify(toGraphDefinition(graph(), requiredInputsRef.current, contextBindingsRef.current)) && !window.confirm('当前流程有未保存的修改，确定替换为示例吗？')) return; render(graph(), demoFlow()); setMessage('已载入示例模板，请保存后再发布。') }}>载入示例模板</button>
      <button disabled={busy} onClick={() => { if (baselineSnapshot.current !== JSON.stringify(toGraphDefinition(graph(), requiredInputsRef.current, contextBindingsRef.current)) && !window.confirm('当前流程有未保存的修改，确定替换为全节点示例吗？')) return; render(graph(), allNodeFlow()); setMessage('已载入全节点示例：先并行执行两条分支，再按 positive 选择条件分支。') }}>载入全节点示例</button>
      <button className="primary-action" disabled={busy || !!requiredInputsError} onClick={() => run(async () => { const current = toGraphDefinition(graph(), requiredInputsRef.current, contextBindingsRef.current); const result = await validateTargetFlow(target, current); setValidatedSnapshot(JSON.stringify(current)); setValidation(result); highlight(result); setMessage(result.valid ? '图校验通过' : `发现 ${result.issues.length} 个问题`) })}>校验流程</button>
      <button className="primary-action" disabled={busy || !validation?.valid || revision === 0 || savedSnapshot !== validatedSnapshot} onClick={() => run(async () => { if (JSON.stringify(toGraphDefinition(graph(), requiredInputsRef.current, contextBindingsRef.current)) !== savedSnapshot) throw new Error('当前画布与已保存草稿不同，请先保存并重新校验'); const published = await publishTargetFlow(target, revision); setMessage(`已发布版本 ${published.versionNumber} · ${published.chainId}`) })}>发布版本</button>
      <button type="button" onClick={() => graph().zoom(0.15, { maxScale: 2 })}>放大</button>
      <button type="button" onClick={() => graph().zoom(-0.15, { minScale: 0.25 })}>缩小</button>
      <button type="button" onClick={() => { graph().zoomToFit({ padding: 24, maxScale: 1 }); graph().centerContent() }}>适应画布</button>
      <button type="button" disabled={busy || nodeCount === 0} onClick={() => run(async () => {
        const current = toGraphDefinition(graph(), requiredInputsRef.current, contextBindingsRef.current)
        render(graph(), { ...current, layout: arrangeGraph(current) })
        setMessage('已按连线整理节点；检查效果后可保存草稿。')
      })}>一键整理</button>
      {!isAsset && <button disabled={busy} onClick={() => run(async () => {
        setRunResult(null)
        const result = await evaluateFlow(code, parseJsonObject(runInputText))
        setRunResult(result)
        setMessage(`试运行 ${result.status} · 版本 ID ${result.versionId}`)
      })}>执行试运行</button>}
    </div><span className="stats">草稿 r{revision} · {nodeCount} 节点 · {edgeCount} 连线</span></section>
    <details className="flow-settings-toggle"><summary>流程输入与上下文变量
      <span>{requiredInputsRef.current?.length ?? 0} 个输入字段 · {contextBindings.length} 个上下文绑定
        {requiredInputsError ? ' · 输入有误' : ''}</span></summary>
    <section className="flow-settings"><label>本流程必需输入字段（逗号或换行分隔）
      <textarea value={requiredInputsText} rows={2} placeholder="如 userId, orderAmount"
        onChange={(event) => changeRequiredInputs(event.target.value)} /></label>
      {requiredInputsError && <p className="form-error" role="alert">{requiredInputsError}</p>}
      <div className="node-config-block"><h3>流程上下文变量绑定</h3>
        <p className="form-note">每个变量在本流程绑定一个上游生产节点。标量节点结果的路径留空；对象结果填写字段路径。</p>
        {contextBindings.map((binding, index) => <div key={index} className="flow-pick-row">
          <label>变量<select aria-label={`上下文变量 ${index + 1}`} value={binding.variableVersionId}
            onChange={(event) => {
              const variable = contextVariables.find((item) => item.id === Number(event.target.value))
              if (variable) changeContextBindings(contextBindings.map((item, i) => i === index
                ? { ...item, variableCode: variable.variableCode, variableVersionId: variable.id } : item))
            }}>
            <option value={0}>请选择</option>
            {binding.variableVersionId > 0 && !contextVariables.some((item) => item.id === binding.variableVersionId)
              && <option value={binding.variableVersionId}>{binding.variableCode} · 固定版本 ID {binding.variableVersionId}</option>}
            {contextVariables.map((item) => <option key={item.id} value={item.id}
              disabled={contextBindings.some((other, otherIndex) => otherIndex !== index
                && other.variableCode === item.variableCode)}>
              {item.variableName} ({item.variableCode}) · v{item.versionNumber}</option>)}
          </select></label>
          <label>生产节点<select aria-label={`生产节点 ${index + 1}`} value={binding.nodeId}
            onChange={(event) => changeContextBindings(contextBindings.map((item, i) => i === index
              ? { ...item, nodeId: event.target.value } : item))}>
            <option value="">请选择</option>{graphRef.current?.getNodes().map((cell) =>
              <option key={cell.id} value={cell.id}>{cell.id}</option>)}
          </select></label>
          <label>结果路径<input aria-label={`结果路径 ${index + 1}`} value={binding.resultPath}
            placeholder="标量留空，或如 businessResult.conclusion"
            onChange={(event) => changeContextBindings(contextBindings.map((item, i) => i === index
              ? { ...item, resultPath: event.target.value } : item))} /></label>
          <button type="button" className="danger" onClick={() => changeContextBindings(
            contextBindings.filter((_, i) => i !== index))}>删除</button>
        </div>)}
        <button type="button" onClick={() => changeContextBindings([...contextBindings,
          { variableCode: '', variableVersionId: 0, nodeId: '', resultPath: '' }])}>添加上下文绑定</button>
      </div>
      {!isAsset && <label>试运行输入 JSON<textarea value={runInputText} rows={2}
        onChange={(event) => setRunInputText(event.target.value)} /></label>}
    </section></details>
    <div className="workspace">
      <details className="panel palette" ref={paletteRef}><summary><strong>添加节点</strong>
        <span>按用途选择节点；新增后自动打开右侧配置</span></summary><div className="palette-content">
        <p>先选用途，添加后在右侧配置；开始和结束各只能有一个。</p>
        <div className="palette-groups">{paletteGroups.map((group) => <section key={group.title} className="palette-group">
          <h3>{group.title}</h3><p>{group.note}</p><div className="palette-options">
          {group.items.map((kind) => <button key={kind} className="palette-item" title={kindHints[kind]}
            disabled={(kind === 'START' || kind === 'END') && graphRef.current?.getNodes().some((cell) =>
              (cell.getData() as FlowNode).kind === kind)} onClick={() => addKind(kind)}>
            <span className="palette-mark" style={{ color: colors[kind] }} aria-hidden="true">{marks[kind]}</span>
            <span><strong>{labels[kind]}</strong><small>{kindHints[kind]}</small></span></button>)}
          </div></section>)}
          {componentCatalog?.some((item) => !item.condition && !['emit-value', 'get-variable', 'execute-rule-set'].includes(item.key))
            && <section className="palette-group"><h3>其他取值组件</h3><p>选择已注册的可复用能力。</p>
              <div className="palette-options">{componentCatalog.filter((item) => !item.condition
                && !['emit-value', 'get-variable', 'execute-rule-set'].includes(item.key)).map((item) =>
                <button key={item.key} className="palette-item" onClick={() => addKind('COMPONENT', item.key)}>
                  <span className="palette-mark" style={{ color: colors.COMPONENT }} aria-hidden="true">↗</span>
                  <span><strong>{item.displayName}</strong><small>{item.key}</small></span></button>)}</div></section>}
          <section className="palette-group"><h3>常用能力</h3><p>配置固定版本后参与当前流程。</p>
            <div className="palette-options">{componentCatalog?.filter((item) =>
              ['execute-rule-set', 'get-variable'].includes(item.key)).map((item) =>
              <button key={item.key} className="palette-item" onClick={() => addKind('COMPONENT', item.key)}>
                <span className="palette-mark" style={{ color: colors.COMPONENT }} aria-hidden="true">↗</span>
                <span><strong>{item.displayName}</strong><small>{item.key === 'execute-rule-set'
                  ? '选规则集发布版本' : '选变量中心版本'}</small></span></button>)}</div></section>
        </div></div></details>
      <section className="panel canvas-panel"><div className="canvas-guide">
        <strong>画布操作</strong><span>从节点输出圆点拖到下一节点输入圆点 · 点击线设置条件分支 · Ctrl + 滚轮缩放</span>
        <button type="button" onClick={() => {
          if (paletteRef.current) { paletteRef.current.open = true; paletteRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' }) }
        }}>＋ 添加节点</button>
      </div><div className={isAsset && code === 'demoCheckoutDecision' ? 'canvas marketing-canvas' : 'canvas'}
        style={canvasHeight === null ? undefined : { height: canvasHeight }} ref={canvasRef} /></section>
      <aside className="panel inspector"><h2>节点与连线</h2>
        {selectedNode && <div className="fields"><p className="kind-hint">{kindHints[selectedNode.kind]}</p>
          <div className="connection-summary"><strong>{nodeTypeLabel(selectedNode)}</strong>
            <span>输入 {selectedIncoming} 条 · 输出 {selectedOutgoing} 条</span>
            <small>从输出圆点拖线，连接到下一节点的输入圆点。</small></div>
          <label>节点 ID<input value={selectedNode.id} readOnly /></label><label>类型<input value={labels[selectedNode.kind]} readOnly /></label>
          {(selectedNode.kind === 'COMPONENT' || selectedNode.kind === 'CONDITION') && <><label>组件<select value={selectedNode.componentKey ?? ''} onChange={(event) => {
            const componentKey = event.target.value
            const config = componentCatalog?.find((item) => item.key === componentKey)?.defaultConfig
              ?? (selectedNode.kind === 'COMPONENT' ? { value: 'demo' }
              : componentKey === 'equals-value'
                ? { sourceNodeId: '', path: 'businessResult.conclusion', expected: '' }
                : { inputKey: 'positive' })
            updateNode({ componentKey, config })
            setConfigText(JSON.stringify(config, null, 2))
          }}>
            {selectedNode.componentKey && !componentOptionsFor(selectedNode.kind, componentCatalog).includes(selectedNode.componentKey) && <option value={selectedNode.componentKey}>{selectedNode.componentKey}（与节点类型不匹配，请重新选择）</option>}
            {componentOptionsFor(selectedNode.kind, componentCatalog).map((key) => <option key={key} value={key}>{componentCatalog?.find((item) => item.key === key)?.displayName ?? key}</option>)}
          </select></label>
            {selectedNode.kind === 'COMPONENT' && selectedNode.componentKey === 'get-variable' &&
              <VariableNodeInspector key={selectedNode.id} node={selectedNode}
                onChange={(config) => updateNode({ config })} />}
            {selectedNode.kind === 'COMPONENT' && selectedNode.componentKey === 'execute-rule-set' &&
              <RuleSetNodeInspector key={selectedNode.id} node={selectedNode}
                onChange={(config) => updateNode({ config })} />}
            {selectedNode.kind === 'COMPONENT' && !['get-variable', 'execute-rule-set'].includes(selectedNode.componentKey ?? '') && <><label>节点配置 JSON<textarea value={configText} onChange={(event) => setConfigText(event.target.value)} rows={6} /></label>
              <button onClick={() => { try { updateNode({ config: parseJsonObject(configText) }); setMessage('节点配置已更新') } catch (error) { setMessage(error instanceof Error ? error.message : 'JSON 格式错误') } }}>应用配置</button></>}
            {selectedNode.kind === 'CONDITION' && selectedNode.componentKey === 'compare-value' &&
              <CompareVariableInspector node={selectedNode}
                candidates={graph().getNodes().map((item) => item.getData() as FlowNode)}
                onChange={(config) => updateNode({ config })} />}
            {selectedNode.kind === 'CONDITION' && selectedNode.componentKey === 'rule-result-equals' &&
              <RuleResultInspector node={selectedNode}
                candidates={graph().getNodes().map((item) => item.getData() as FlowNode)}
                onChange={(config) => updateNode({ config })} />}
            {selectedNode.kind === 'CONDITION' && !['compare-value', 'rule-result-equals'].includes(selectedNode.componentKey ?? '') && (selectedNode.componentKey === 'equals-value'
              ? <><label>上游节点 ID<input value={String(selectedNode.config.sourceNodeId ?? '')} placeholder="如 eligibilityCall"
                  onChange={(event) => updateNode({ config: { ...selectedNode.config, sourceNodeId: event.target.value } })} /></label>
                <label>结果路径<input value={String(selectedNode.config.path ?? '')} placeholder="businessResult.conclusion"
                  onChange={(event) => updateNode({ config: { ...selectedNode.config, path: event.target.value } })} /></label>
                <label>预期值<input value={String(selectedNode.config.expected ?? '')} placeholder="如 ELIGIBLE"
                  onChange={(event) => updateNode({ config: { ...selectedNode.config, expected: event.target.value } })} /></label></>
              : <label>请求输入字段<input value={String(selectedNode.config.inputKey ?? '')} placeholder="如 positive"
                  onChange={(event) => updateNode({ config: { ...selectedNode.config, inputKey: event.target.value } })} /></label>)}</>}
          {selectedNode.kind === 'RESULT' && <ResultNodeInspector key={selectedNode.id} node={selectedNode}
            candidates={graph().getNodes().map((item) => item.getData() as FlowNode)}
            onChange={(config) => updateNode({ config })} />}
          {selectedNode.kind === 'SUBFLOW' && <SubflowNodeInspector key={selectedNode.id} node={selectedNode}
            ownerFlowCode={ownerFlowCode} onChange={(config) => updateNode({ config })} />}
        </div>}
        {selectedEdge && <div className="fields"><div className="connection-summary">
          <strong>{displayNode(edgeSourceId)} → {displayNode(edgeTargetId)}</strong>
          <span>{sourceIsCondition ? '条件分支连线' : '普通执行连线'}</span>
        </div><label>连线 ID<input value={selected?.id ?? ''} readOnly /></label>
          {sourceIsCondition ? <><p className="kind-hint">条件节点必须有两条出线：一条“满足”，一条“不满足”。标签决定实际执行哪条路径。</p>
            <label>当前连线代表<select aria-label="条件分支标签" value={selectedEdge.branch ?? ''}
              onChange={(event) => updateBranch((event.target.value || null) as FlowEdge['branch'])}>
              <option value="">请选择分支</option>
              <option value="true" disabled={siblingBranches.includes('true')}>满足（true）</option>
              <option value="false" disabled={siblingBranches.includes('false')}>不满足（false）</option>
            </select></label></>
            : <><p className="kind-hint">普通连线按箭头方向依次执行，不需要分支标签。</p>
              {selectedEdge.branch && <button type="button" onClick={() => updateBranch(null)}>
                清除无效分支标签</button>}</>}
          <div className="edge-navigation"><button type="button" onClick={() => focusCell(edgeSourceId)}
            disabled={!edgeSourceId}>查看起点节点</button>
            <button type="button" onClick={() => focusCell(edgeTargetId)} disabled={!edgeTargetId}>
              查看终点节点</button></div>
        </div>}
        {selected && <button className="danger" onClick={() => { graph().removeCell(selected.id); setSelected(null) }}>删除选中项</button>}
        {!selected && <div className="inspector-empty"><strong>先选中画布内容</strong>
          <p>点击节点配置执行能力；点击连线核对方向。条件节点的两条出线需分别标为“满足”和“不满足”。</p></div>}
        <div className="issues"><h2>校验结果</h2>{validation?.issues.length ? validation.issues.map((issue, i) =>
          <button key={i} type="button" className="issue" onClick={() => {
            const id = issue.nodeId ?? issue.edgeId
            if (id) focusCell(id)
          }}>{issue.code} · {issue.nodeId ?? issue.edgeId ?? '流程'}<br />{issueHints[issue.code] ?? issue.message}
            {(issue.nodeId || issue.edgeId) && <span>点击定位</span>}</button>) :
          <p>{validation?.valid ? '校验通过，可以保存并发布。' : '校验后会在这里列出问题；点击问题可定位节点或连线。'}</p>}</div>
      </aside>
    </div>
    {runResult && <DecisionRunResult result={runResult} />}
    <footer className="message" role="status">{message}</footer>
  </div></main>
}
