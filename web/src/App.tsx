import { useEffect, useRef, useState } from 'react'
import { Graph } from '@antv/x6'

const demoNodes = [
  { id: 'prepare', label: 'Prepare', x: 100, y: 180, color: '#1677ff' },
  { id: 'parallel-left', label: 'Parallel Left', x: 360, y: 100, color: '#52c41a' },
  { id: 'parallel-right', label: 'Parallel Right', x: 360, y: 260, color: '#fa8c16' },
]

export default function App() {
  const containerRef = useRef<HTMLDivElement>(null)
  const graphRef = useRef<Graph | null>(null)
  const [nodeCount, setNodeCount] = useState(demoNodes.length)
  const [edgeCount, setEdgeCount] = useState(2)

  useEffect(() => {
    if (!containerRef.current) return
    const graph = new Graph({
      container: containerRef.current,
      background: { color: '#f7f9fc' },
      grid: { size: 10, visible: true },
      panning: true,
      mousewheel: { enabled: true, modifiers: 'ctrl' },
      connecting: {
        allowBlank: false,
        allowLoop: false,
        allowNode: false,
        snap: true,
        router: 'orth',
        connector: 'rounded',
      },
    })

    demoNodes.forEach((item) => {
      graph.addNode({
        id: item.id,
        shape: 'rect',
        x: item.x,
        y: item.y,
        width: 150,
        height: 56,
        attrs: {
          body: { fill: '#fff', stroke: item.color, strokeWidth: 2, rx: 8, ry: 8 },
          label: { text: item.label, fill: '#1f2937', fontSize: 14 },
        },
      })
    })
    graph.addEdge({ source: 'prepare', target: 'parallel-left' })
    graph.addEdge({ source: 'prepare', target: 'parallel-right' })
    graph.on('cell:added', () => {
      setNodeCount(graph.getNodes().length)
      setEdgeCount(graph.getEdges().length)
    })
    graph.on('cell:removed', () => {
      setNodeCount(graph.getNodes().length)
      setEdgeCount(graph.getEdges().length)
    })
    graphRef.current = graph
    return () => graph.dispose()
  }, [])

  const addNode = () => {
    const graph = graphRef.current
    if (!graph) return
    const id = `component-${nodeCount + 1}`
    graph.addNode({
      id,
      shape: 'rect',
      x: 650,
      y: 180 + nodeCount * 12,
      width: 150,
      height: 56,
      attrs: { body: { fill: '#fff', stroke: '#722ed1', strokeWidth: 2, rx: 8, ry: 8 }, label: { text: id } },
    })
  }

  const clearGraph = () => graphRef.current?.clearCells(graphRef.current.getCells())

  return (
    <main className="page">
      <header className="header">
        <div><p className="eyebrow">RISK CONTROL DECISION</p><h1>流程编排基线</h1></div>
        <div className="actions"><button onClick={addNode}>添加组件</button><button className="secondary" onClick={clearGraph}>清空画布</button></div>
      </header>
      <section className="panel"><div className="panel-header"><div><h2>LiteFlow 示例流程</h2><p>当前仅用于验证 X6 节点与连线编辑，数据保存在浏览器内存。</p></div><div className="stats"><span>{nodeCount} 节点</span><span>{edgeCount} 连线</span></div></div><div className="canvas" ref={containerRef} /></section>
    </main>
  )
}
