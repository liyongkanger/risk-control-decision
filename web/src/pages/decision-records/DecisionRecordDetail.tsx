import { Alert, Descriptions, Drawer, Empty, Tag, Typography } from 'antd'
import type { DecisionNodeTrace, DecisionRecord } from '../../services/decisionRecords'
import './decision-records.css'

type Props = { record: DecisionRecord | null; loading: boolean; error: string | null; open: boolean;
  onClose: () => void }
const asObject = (value: unknown): Record<string, unknown> | null => value && typeof value === 'object'
  && !Array.isArray(value) ? value as Record<string, unknown> : null
const asTrace = (value: unknown): DecisionNodeTrace[] => Array.isArray(value)
  ? value.filter((item) => typeof asObject(item)?.nodeId === 'string') as DecisionNodeTrace[] : []
const formatted = (value: unknown) => JSON.stringify(value, null, 2) ?? '无数据'
const traceTime = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function TraceNodes({ traces, outputs, depth = 0 }: { traces: DecisionNodeTrace[];
  outputs: Record<string, unknown>; depth?: number }) {
  if (traces.length === 0) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本次没有节点轨迹" />
  return <div className={depth ? 'decision-trace-children' : 'decision-trace-list'}>
    {[...traces].sort((a, b) => a.startedAt.localeCompare(b.startedAt)).map((trace, index) => {
      const output = outputs[trace.nodeId]
      const object = asObject(output)
      const childTrace = asTrace(object?.trace ?? object?.childTrace)
      const childOutputs = asObject(object?.nodeResults) ?? {}
      return <div className="decision-trace-node" key={`${trace.requestId}:${trace.nodeId}:${index}`}>
        <div className="decision-trace-heading"><span className="decision-trace-index">{index + 1}</span>
          <strong>{trace.nodeId}</strong><Tag color={trace.status === 'SUCCEEDED' ? 'green' : 'red'}>
            {trace.status === 'SUCCEEDED' ? '成功' : '失败'}</Tag></div>
        <div className="decision-trace-meta">流程 {trace.sceneCode} · v{trace.flowVersion}
          {trace.startedAt && ` · ${traceTime(trace.startedAt)}`}
          {trace.errorCode && ` · ${trace.errorCode}`}</div>
        {typeof object?.flowCode === 'string' && <div className="decision-trace-subflow">调用子流程 {object.flowCode}
          {object.versionId != null && ` · 固定版本 ID ${String(object.versionId)}`}</div>}
        {childTrace.length > 0 && <TraceNodes traces={childTrace} outputs={childOutputs} depth={depth + 1} />}
        {output !== undefined && <details className="decision-trace-raw"><summary>查看该节点输出</summary>
          <pre>{formatted(output)}</pre></details>}
      </div>
    })}
  </div>
}

/** 跨场景展示一次执行的原始节点轨迹和最终业务结果。 */
export default function DecisionRecordDetail({ record, loading, error, open, onClose }: Props) {
  const result = record?.result
  return <Drawer title="决策执行详情" size={800} open={open} loading={loading} onClose={onClose}>
    {error && <Alert type="error" showIcon title="记录读取失败" description={error} />}
    {record && <>
      <div className="decision-record-outcome">
        <Tag color={record.status === 'SUCCEEDED' ? 'green' : 'red'}>
          {record.status === 'SUCCEEDED' ? '执行成功' : '执行失败'}</Tag>
        <h3>{result?.businessResult?.conclusion ?? record.errorCode ?? '该版本未定义业务结果'}</h3>
        <p>{result?.businessResult?.reason ?? (record.status === 'FAILED'
          ? '查看下方失败节点和错误码。' : '执行已完成，当前版本未返回业务结论。')}</p>
      </div>
      <Descriptions size="small" column={2} items={[
        { key: 'executionId', label: '执行 ID', children: record.executionId },
        { key: 'requestId', label: '请求标识', children: record.requestId },
        { key: 'scene', label: '场景', children: record.sceneCode },
        { key: 'version', label: '入口固定版本 ID', children: record.versionId },
        { key: 'time', label: '执行记录时间', children: record.createdAt?.replace('T', ' ') },
        { key: 'reason', label: '业务原因码 / 错误码', children: result?.businessResult?.reasonCode
          ?? record.errorCode ?? '无' },
      ]} />
      <Typography.Title level={5} style={{ marginTop: 24 }}>实际执行的节点</Typography.Title>
      <Typography.Paragraph type="secondary">按节点开始时间排列；子流程展开在调用节点下。跳过的节点不会出现在轨迹中。</Typography.Paragraph>
      <TraceNodes traces={result?.trace ?? []} outputs={result?.nodeResults ?? {}} />
      <details className="decision-trace-raw decision-record-input"><summary>查看本次输入快照</summary>
        <pre>{record.inputJson ?? formatted(record.input)}</pre></details>
      <details className="decision-trace-raw"><summary>查看完整执行结果 JSON</summary>
        <pre>{record.resultJson ?? formatted(result)}</pre></details>
    </>}
  </Drawer>
}
