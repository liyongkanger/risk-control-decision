import { useEffect, useRef, useState } from 'react'
import { Graph } from '@antv/x6'
import { evaluateFlow, loadDraft, publishFlow, saveDraft, validateFlow } from './api'
import { toGraphDefinition } from './graphAdapter'
import type { FlowEdge, FlowNode, FlowValidation, GraphDefinition, NodeKind } from './types'

const kinds: NodeKind[] = ['START', 'END', 'COMPONENT', 'PARALLEL_SPLIT', 'PARALLEL_JOIN', 'CONDITION', 'CONDITION_JOIN']
const labels: Record<NodeKind, string> = {
  START: '开始', END: '结束', COMPONENT: '组件', PARALLEL_SPLIT: '并行拆分',
  PARALLEL_JOIN: '并行汇合', CONDITION: '条件', CONDITION_JOIN: '条件汇合',
}
const colors: Record<NodeKind, string> = {
  START: '#1677ff', END: '#667085', COMPONENT: '#16a34a', PARALLEL_SPLIT: '#9333ea',
  PARALLEL_JOIN: '#9333ea', CONDITION: '#d97706', CONDITION_JOIN: '#d97706',
}

function sample(): GraphDefinition {
  return {
    schemaVersion: 1,
    nodes: [
      { id: 'start', kind: 'START', componentKey: null, failurePolicy: null, config: {} },
      { id: 'emit', kind: 'COMPONENT', componentKey: 'emit-value', failurePolicy: 'FAIL_FAST', config: { value: 'demo' } },
      { id: 'end', kind: 'END', componentKey: null, failurePolicy: null, config: {} },
    ],
    edges: [
      { id: 'edge1', source: 'start', target: 'emit', branch: null },
      { id: 'edge2', source: 'emit', target: 'end', branch: null },
    ],
    layout: { start: { x: 80, y: 220 }, emit: { x: 330, y: 220 }, end: { x: 580, y: 220 } },
  }
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

export default function FlowEditor() {
  const canvasRef = useRef<HTMLDivElement>(null)
  const graphRef = useRef<Graph | null>(null)
  const nextId = useRef(4)
  const [sceneCode, setSceneCode] = useState('demoScene')
  const [revision, setRevision] = useState(0)
  const [selected, setSelected] = useState<{ id: string; type: 'node' | 'edge' } | null>(null)
  const [configText, setConfigText] = useState('{}')
  const [validation, setValidation] = useState<FlowValidation | null>(null)
  const [message, setMessage] = useState('从节点右侧连接点拖到下一节点左侧连接点。')
  const [nodeCount, setNodeCount] = useState(3)
  const [edgeCount, setEdgeCount] = useState(2)
  const [busy, setBusy] = useState(false)

  const refresh = (graph: Graph) => {
    setNodeCount(graph.getNodes().length)
    setEdgeCount(graph.getEdges().length)
    setValidation(null)
  }

  const render = (graph: Graph, definition: GraphDefinition) => {
    graph.clearCells(graph.getCells())
    definition.nodes.forEach((node, index) => drawNode(graph, node, definition.layout[node.id] ?? { x: 80 + index * 200, y: 180 }))
    definition.edges.forEach((edge) => drawEdge(graph, edge))
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
    graph.on('cell:click', ({ cell }) => {
      const type = cell.isNode() ? 'node' : 'edge'
      setSelected({ id: cell.id, type })
      setConfigText(type === 'node' ? JSON.stringify((cell.getData() as FlowNode)?.config ?? {}, null, 2) : '{}')
    })
    render(graph, sample())
    return () => { graphRef.current = null; graph.dispose() }
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
    const id = `${kind.toLowerCase().replaceAll('_', '')}${nextId.current++}`
    drawNode(graph(), {
      id, kind, componentKey: kind === 'COMPONENT' ? 'emit-value' : kind === 'CONDITION' ? 'is-positive' : null,
      failurePolicy: kind === 'COMPONENT' ? 'FAIL_FAST' : null,
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
  }

  const updateBranch = (branch: FlowEdge['branch']) => {
    if (!selected || selected.type !== 'edge') return
    const cell = graph().getCellById(selected.id)
    if (!cell?.isEdge()) return
    cell.setData({ branch })
    cell.setLabels(branch ? [branch] : [])
    setValidation(null)
  }

  const cell = selected ? graphRef.current?.getCellById(selected.id) : null
  const selectedNode = cell?.isNode() ? cell.getData() as FlowNode : null
  const selectedEdge = cell?.isEdge() ? cell.getData() as { branch: FlowEdge['branch'] } : null

  return <main className="page">
    <header className="header"><div><p className="eyebrow">RISK CONTROL DECISION</p><h1>场景流程编辑器</h1><p className="subtitle">X6 编辑 · LiteFlow 执行</p></div>
      <div className="scene"><label htmlFor="sceneCode">场景码</label><input id="sceneCode" value={sceneCode} onChange={(event) => { setSceneCode(event.target.value); setRevision(0); setValidation(null) }} /></div></header>
    <section className="toolbar"><div className="actions">
      <button disabled={busy} onClick={() => run(async () => { const draft = await loadDraft(sceneCode); if (!draft) { setMessage('该场景没有草稿'); return }; render(graph(), draft.graph); setRevision(draft.revision); setMessage(`已加载草稿 r${draft.revision}`) })}>加载草稿</button>
      <button disabled={busy} onClick={() => run(async () => { const saved = await saveDraft(sceneCode, toGraphDefinition(graph()), revision); setRevision(saved.revision); setMessage(`已保存草稿 r${saved.revision}`) })}>保存草稿</button>
      <button disabled={busy} onClick={() => run(async () => { const result = await validateFlow(sceneCode, toGraphDefinition(graph())); setValidation(result); highlight(result); setMessage(result.valid ? '图校验通过' : `发现 ${result.issues.length} 个问题`) })}>校验流程</button>
      <button disabled={busy || !validation?.valid || revision === 0} onClick={() => run(async () => { const published = await publishFlow(sceneCode, revision); setMessage(`已发布版本 ${published.versionNumber} · ${published.chainId}`) })}>发布版本</button>
      <button disabled={busy} onClick={() => run(async () => { const result = await evaluateFlow(sceneCode, { positive: true }); setMessage(`执行 ${result.status} · 版本 ${result.versionId} · ${JSON.stringify(result.nodeResults)}`) })}>执行示例</button>
    </div><span className="stats">草稿 r{revision} · {nodeCount} 节点 · {edgeCount} 连线</span></section>
    <div className="workspace">
      <aside className="panel palette"><h2>节点类型</h2><p>点击添加到画布</p>{kinds.map((kind) => <button key={kind} className="palette-item" onClick={() => addKind(kind)}>{labels[kind]}</button>)}</aside>
      <section className="panel canvas-panel"><div className="canvas" ref={canvasRef} /></section>
      <aside className="panel inspector"><h2>节点与连线</h2>
        {selectedNode && <div className="fields"><label>节点 ID<input value={selectedNode.id} readOnly /></label><label>类型<input value={labels[selectedNode.kind]} readOnly /></label>
          {(selectedNode.kind === 'COMPONENT' || selectedNode.kind === 'CONDITION') && <><label>组件<select value={selectedNode.componentKey ?? ''} onChange={(event) => updateNode({ componentKey: event.target.value })}><option value="emit-value">emit-value</option><option value="is-positive">is-positive</option></select></label>
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
  </main>
}
