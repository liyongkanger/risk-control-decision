import { useEffect, useRef, useState } from 'react'
import { Graph } from '@antv/x6'
import { history } from '@umijs/max'
import { evaluateFlow, loadTargetDraft, publishTargetFlow, saveTargetDraft, validateTargetFlow } from './api'
import type { FlowTarget } from './api'
import { toGraphDefinition } from './graphAdapter'
import { componentOptionsFor } from './componentOptions'
import { allNodeFlow, demoFlow, emptyFlow } from './templates'
import type { FlowEdge, FlowNode, FlowValidation, GraphDefinition, NodeKind } from './types'
import { getScene } from '../services/scene'
import { getFlow } from '../services/flow'
import './editor.css'

const kinds: NodeKind[] = ['START', 'END', 'COMPONENT', 'PARALLEL_SPLIT', 'PARALLEL_JOIN', 'CONDITION', 'CONDITION_JOIN']
const labels: Record<NodeKind, string> = {
  START: '开始', END: '结束', COMPONENT: '组件', PARALLEL_SPLIT: '并行拆分',
  PARALLEL_JOIN: '并行汇合', CONDITION: '条件', CONDITION_JOIN: '条件汇合',
}
const colors: Record<NodeKind, string> = {
  START: '#1677ff', END: '#667085', COMPONENT: '#16a34a', PARALLEL_SPLIT: '#9333ea',
  PARALLEL_JOIN: '#9333ea', CONDITION: '#d97706', CONDITION_JOIN: '#d97706',
}
const kindHints: Record<NodeKind, string> = {
  START: '流程入口，每条流程只有一个。',
  END: '流程出口，每条流程只有一个；当前不负责计算业务决策结论。',
  COMPONENT: '调用一个已注册组件并保存它的节点结果。',
  PARALLEL_SPLIT: '同时启动所有后续分支。',
  PARALLEL_JOIN: '等待所有并行分支结束后继续；当前只负责结构汇合。',
  CONDITION: '根据条件组件的 true／false 结果选择一条分支。',
  CONDITION_JOIN: '把选中的条件分支接回主路径；当前只负责结构汇合。',
}

function drawNode(graph: Graph, node: FlowNode, position: { x: number; y: number }) {
  graph.addNode({
    id: node.id, shape: 'rect', x: position.x, y: position.y, width: 155, height: 60, data: node,
    attrs: {
      body: { fill: '#fff', stroke: colors[node.kind], strokeWidth: 2, rx: 8, ry: 8 },
      label: { text: `${labels[node.kind]} · ${node.id}`, fill: '#1f2937', fontSize: 12 },
    },
    ports: {
      groups: {
        in: { position: 'left', attrs: { circle: { r: 5, magnet: true, stroke: colors[node.kind], fill: '#fff' } } },
        out: { position: 'right', attrs: { circle: { r: 5, magnet: true, stroke: colors[node.kind], fill: '#fff' } } },
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
    data: { branch: edge.branch }, labels: edge.branch ? [edge.branch] : [],
    attrs: { line: { stroke: '#8292a8', strokeWidth: 2, targetMarker: 'classic' } },
  })
}

/** 同时服务场景入口与独立流程资产的 X6 流程设计器。 */
export default function FlowEditor({ target }: { target: FlowTarget }) {
  const code = target.code
  const isAsset = target.kind === 'flow'
  const canvasRef = useRef<HTMLDivElement>(null)
  const graphRef = useRef<Graph | null>(null)
  const baselineSnapshot = useRef<string | null>(null)
  const nextId = useRef(4)
  const [ownerName, setOwnerName] = useState('')
  const [ownerError, setOwnerError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [selected, setSelected] = useState<{ id: string; type: 'node' | 'edge' } | null>(null)
  const [configText, setConfigText] = useState('{}')
  const [validation, setValidation] = useState<FlowValidation | null>(null)
  const [savedSnapshot, setSavedSnapshot] = useState<string | null>(null)
  const [validatedSnapshot, setValidatedSnapshot] = useState<string | null>(null)
  const [message, setMessage] = useState('从节点右侧连接点拖到下一节点左侧连接点。')
  const [nodeCount, setNodeCount] = useState(3)
  const [edgeCount, setEdgeCount] = useState(2)
  const [busy, setBusy] = useState(false)
  const [positiveInput, setPositiveInput] = useState(true)
  const [, forceRender] = useState(0)

  const refresh = (graph: Graph) => {
    setNodeCount(graph.getNodes().length)
    setEdgeCount(graph.getEdges().length)
    setValidation(null)
    setValidatedSnapshot(null)
  }

  const render = (graph: Graph, definition: GraphDefinition) => {
    graph.resetCells([])
    definition.nodes.forEach((node, index) => drawNode(graph, node, definition.layout[node.id] ?? { x: 80 + index * 200, y: 180 }))
    definition.edges.forEach((edge) => drawEdge(graph, edge))
    graph.zoomToFit({ padding: 24, maxScale: 1 })
    graph.centerContent()
    nextId.current = definition.nodes.length + 1
    setSelected(null)
    refresh(graph)
  }

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
    graph.on('node:change:position', () => { setValidation(null); setValidatedSnapshot(null) })
    graph.on('cell:click', ({ cell }) => {
      const type = cell.isNode() ? 'node' : 'edge'
      setSelected({ id: cell.id, type })
      setConfigText(type === 'node' ? JSON.stringify((cell.getData() as FlowNode)?.config ?? {}, null, 2) : '{}')
    })
    render(graph, emptyFlow())
    baselineSnapshot.current = JSON.stringify(toGraphDefinition(graph))
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
        const name = isAsset ? (await getFlow(code)).flowName : (await getScene(code)).sceneName
        if (!active) return
        setOwnerName(name)
        setOwnerError(null)
        const draft = await loadTargetDraft(target)
        if (!active || !graphRef.current) return
        render(graphRef.current, draft?.graph ?? emptyFlow())
        setRevision(draft?.revision ?? 0)
        const snapshot = JSON.stringify(toGraphDefinition(graphRef.current))
        baselineSnapshot.current = snapshot
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
    const hasUnsavedChanges = () => graphRef.current && baselineSnapshot.current !== null
      && JSON.stringify(toGraphDefinition(graphRef.current)) !== baselineSnapshot.current
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (hasUnsavedChanges()) { event.preventDefault(); event.returnValue = '' }
    }
    const unblock = history.block((transition) => {
      if (hasUnsavedChanges() && !window.confirm('当前流程有未保存的修改，确定离开吗？')) return
      unblock()
      transition.retry()
    })
    window.addEventListener('beforeunload', beforeUnload)
    return () => { unblock(); window.removeEventListener('beforeunload', beforeUnload) }
  }, [])

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

  const addKind = (kind: NodeKind) => {
    const prefix = kind.toLowerCase().replaceAll('_', '')
    let id = `${prefix}${nextId.current++}`
    while (graph().getCellById(id)) id = `${prefix}${nextId.current++}`
    drawNode(graph(), {
      id, kind, componentKey: kind === 'COMPONENT' ? 'emit-value' : kind === 'CONDITION' ? 'is-positive' : null,
      failurePolicy: kind === 'COMPONENT' || kind === 'CONDITION' ? 'FAIL_FAST' : null,
      config: kind === 'COMPONENT' ? { value: 'demo' } : kind === 'CONDITION' ? { inputKey: 'positive' } : {},
    }, { x: 190 + (nextId.current % 4) * 85, y: 80 + (nextId.current % 5) * 90 })
  }

  const updateNode = (changes: Partial<FlowNode>) => {
    if (!selected || selected.type !== 'node') return
    const cell = graph().getCellById(selected.id)
    if (!cell?.isNode()) return
    const next = { ...(cell.getData() as FlowNode), ...changes }
    cell.setData(next)
    cell.attr('label/text', `${labels[next.kind]} · ${next.id}`)
    setValidation(null)
    setValidatedSnapshot(null)
    forceRender((value) => value + 1)
  }

  const updateBranch = (branch: FlowEdge['branch']) => {
    if (!selected || selected.type !== 'edge') return
    const cell = graph().getCellById(selected.id)
    if (!cell?.isEdge()) return
    cell.setData({ branch })
    cell.setLabels(branch ? [branch] : [])
    setValidation(null)
    setValidatedSnapshot(null)
    forceRender((value) => value + 1)
  }

  const cell = selected ? graphRef.current?.getCellById(selected.id) : null
  const selectedNode = cell?.isNode() ? cell.getData() as FlowNode : null
  const selectedEdge = cell?.isEdge() ? cell.getData() as { branch: FlowEdge['branch'] } : null

  if (ownerError) return <main className="flow-editor"><p role="alert">{ownerError}</p><button onClick={() => history.push(isAsset ? '/flows' : '/scenes')}>返回{isAsset ? '流程资产' : '场景管理'}</button></main>

  return <main className="flow-editor"><div className="page">
    <header className="header"><div><p className="eyebrow">RISK CONTROL DECISION</p><h1>{ownerName || (isAsset ? '流程' : '场景')} · 流程设计</h1><p className="subtitle">X6 编辑 · LiteFlow 执行</p></div>
      <div className="scene"><label htmlFor="ownerCode">{isAsset ? '流程码' : '场景码'}</label><input id="ownerCode" value={code} readOnly /></div></header>
    <section className="toolbar"><div className="actions">
      <button disabled={busy} onClick={() => run(async () => { const draft = await loadTargetDraft(target); if (!draft) { setMessage('该流程没有草稿'); return }; if (baselineSnapshot.current !== JSON.stringify(toGraphDefinition(graph())) && !window.confirm('当前流程有未保存的修改，确定加载草稿吗？')) return; render(graph(), draft.graph); setRevision(draft.revision); const snapshot = JSON.stringify(toGraphDefinition(graph())); baselineSnapshot.current = snapshot; setSavedSnapshot(snapshot); setMessage(`已加载草稿 r${draft.revision}`) })}>加载草稿</button>
      <button disabled={busy} onClick={() => run(async () => { const current = toGraphDefinition(graph()); const snapshot = JSON.stringify(current); const saved = await saveTargetDraft(target, current, revision); setRevision(saved.revision); setSavedSnapshot(snapshot); baselineSnapshot.current = snapshot; if (validatedSnapshot !== snapshot) setValidation(null); setMessage(`已保存草稿 r${saved.revision}`) })}>保存草稿</button>
      <button disabled={busy} onClick={() => { if (baselineSnapshot.current !== JSON.stringify(toGraphDefinition(graph())) && !window.confirm('当前流程有未保存的修改，确定替换为示例吗？')) return; render(graph(), demoFlow()); setMessage('已载入示例模板，请保存后再发布。') }}>载入示例模板</button>
      <button disabled={busy} onClick={() => { if (baselineSnapshot.current !== JSON.stringify(toGraphDefinition(graph())) && !window.confirm('当前流程有未保存的修改，确定替换为全节点示例吗？')) return; render(graph(), allNodeFlow()); setMessage('已载入全节点示例：先并行执行两条分支，再按 positive 选择条件分支。') }}>载入全节点示例</button>
      <button disabled={busy} onClick={() => run(async () => { const current = toGraphDefinition(graph()); const result = await validateTargetFlow(target, current); setValidatedSnapshot(JSON.stringify(current)); setValidation(result); highlight(result); setMessage(result.valid ? '图校验通过' : `发现 ${result.issues.length} 个问题`) })}>校验流程</button>
      <button disabled={busy || !validation?.valid || revision === 0 || savedSnapshot !== validatedSnapshot} onClick={() => run(async () => { if (JSON.stringify(toGraphDefinition(graph())) !== savedSnapshot) throw new Error('当前画布与已保存草稿不同，请先保存并重新校验'); const published = await publishTargetFlow(target, revision); setMessage(`已发布版本 ${published.versionNumber} · ${published.chainId}`) })}>发布版本</button>
      <button type="button" onClick={() => graph().zoom(0.15, { maxScale: 2 })}>放大</button>
      <button type="button" onClick={() => graph().zoom(-0.15, { minScale: 0.25 })}>缩小</button>
      <button type="button" onClick={() => { graph().zoomToFit({ padding: 24, maxScale: 1 }); graph().centerContent() }}>适应画布</button>
      {!isAsset && <><label className="sample-input">输入 positive
        <select value={String(positiveInput)} onChange={(event) => setPositiveInput(event.target.value === 'true')}>
          <option value="true">true</option><option value="false">false</option>
        </select>
      </label><button disabled={busy} onClick={() => run(async () => { const result = await evaluateFlow(code, { positive: positiveInput }); setMessage(`执行 ${result.status} · 版本 ${result.versionId} · ${JSON.stringify(result.nodeResults)}`) })}>执行示例</button></>}
    </div><span className="stats">草稿 r{revision} · {nodeCount} 节点 · {edgeCount} 连线</span></section>
    <div className="workspace">
      <aside className="panel palette"><h2>节点类型</h2><p>点击添加到画布；可放大查看节点、拖动画布移动。</p>{kinds.map((kind) => <button key={kind} className="palette-item" title={kindHints[kind]} onClick={() => addKind(kind)}>{labels[kind]}</button>)}</aside>
      <section className="panel canvas-panel"><div className="canvas" ref={canvasRef} /></section>
      <aside className="panel inspector"><h2>节点与连线</h2>
        {selectedNode && <div className="fields"><p className="kind-hint">{kindHints[selectedNode.kind]}</p><label>节点 ID<input value={selectedNode.id} readOnly /></label><label>类型<input value={labels[selectedNode.kind]} readOnly /></label>
          {(selectedNode.kind === 'COMPONENT' || selectedNode.kind === 'CONDITION') && <><label>组件<select value={selectedNode.componentKey ?? ''} onChange={(event) => updateNode({ componentKey: event.target.value })}>
            {selectedNode.componentKey && !componentOptionsFor(selectedNode.kind).includes(selectedNode.componentKey) && <option value={selectedNode.componentKey}>{selectedNode.componentKey}（与节点类型不匹配，请重新选择）</option>}
            {componentOptionsFor(selectedNode.kind).map((key) => <option key={key} value={key}>{key}</option>)}
          </select></label>
            <label>节点配置 JSON<textarea value={configText} onChange={(event) => setConfigText(event.target.value)} rows={6} /></label>
            <button onClick={() => { try { updateNode({ config: JSON.parse(configText) }); setMessage('节点配置已更新') } catch { setMessage('JSON 格式错误') } }}>应用配置</button></>}
        </div>}
        {selectedEdge && <div className="fields"><label>连线 ID<input value={selected?.id ?? ''} readOnly /></label><label>分支标签<select value={selectedEdge.branch ?? ''} onChange={(event) => updateBranch((event.target.value || null) as FlowEdge['branch'])}><option value="">无</option><option value="true">true</option><option value="false">false</option></select></label></div>}
        {selected && <button className="danger" onClick={() => { graph().removeCell(selected.id); setSelected(null) }}>删除选中项</button>}
        {!selected && <p>点击画布中的节点或连线，可编辑配置与分支标签。</p>}
        <div className="issues"><h2>校验结果</h2>{validation?.issues.length ? validation.issues.map((issue, i) => <p key={i} className="issue">{issue.code} · {issue.nodeId ?? issue.edgeId ?? '流程'}<br />{issue.message}</p>) : <p>尚无问题</p>}</div>
      </aside>
    </div>
    <footer className="message" role="status">{message}</footer>
  </div></main>
}
