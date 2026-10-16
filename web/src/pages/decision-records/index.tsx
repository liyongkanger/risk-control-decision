import { PageContainer } from '@ant-design/pro-components'
import { history, useLocation } from '@umijs/max'
import { Alert, Button, Input, Select, Space, Table, Tag, Typography } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { getDecisionRecord, listDecisionRecords } from '../../services/decisionRecords'
import type { DecisionRecord, DecisionRecordPage } from '../../services/decisionRecords'
import DecisionRecordDetail from './DecisionRecordDetail'

/** 按场景与请求标识查询每一次场景决策的执行快照。 */
export default function DecisionRecordsPage() {
  const location = useLocation()
  const directId = new URLSearchParams(location.search).get('executionId')
  const [sceneText, setSceneText] = useState('')
  const [requestText, setRequestText] = useState('')
  const [statusText, setStatusText] = useState<string | undefined>()
  const [filters, setFilters] = useState({ sceneCode: '', requestId: '', status: '' })
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [data, setData] = useState<DecisionRecordPage>({ records: [], total: 0, page: 1, pageSize: 10 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [detail, setDetail] = useState<DecisionRecord | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [openedId, setOpenedId] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setData(await listDecisionRecords({ ...filters, page, pageSize }))
      setError(null)
    } catch (cause) { setError(cause instanceof Error ? cause.message : '记录读取失败') }
    finally { setLoading(false) }
  }, [filters, page, pageSize])
  useEffect(() => { void reload() }, [reload])

  useEffect(() => {
    if (!directId) { setOpenedId(null); setDetail(null); return }
    setOpenedId(directId); setDetail(null); setDetailError(null); setDetailLoading(true)
    let active = true
    void getDecisionRecord(directId).then((value) => { if (active) setDetail(value) })
      .catch((cause) => { if (active) setDetailError(cause instanceof Error
        ? cause.message : '记录读取失败') })
      .finally(() => { if (active) setDetailLoading(false) })
    return () => { active = false }
  }, [directId])

  const search = () => {
    setFilters({ sceneCode: sceneText.trim(), requestId: requestText.trim(), status: statusText ?? '' })
    setPage(1)
  }
  const close = () => history.replace('/decision-records')

  return <PageContainer title="决策记录">
    <Typography.Paragraph type="secondary">每次执行有独立 ID，可追溯当时的请求、固定版本、业务结果及实际节点轨迹。</Typography.Paragraph>
    <Space wrap style={{ marginBottom: 16 }}>
      <Input aria-label="筛选场景码" placeholder="场景码" value={sceneText}
        onChange={(event) => setSceneText(event.target.value)} onPressEnter={search} style={{ width: 180 }} />
      <Input aria-label="筛选请求标识" placeholder="请求标识（精确匹配）" value={requestText}
        onChange={(event) => setRequestText(event.target.value)} onPressEnter={search} style={{ width: 230 }} />
      <Select aria-label="筛选执行状态" allowClear placeholder="全部状态" value={statusText}
        onChange={setStatusText} style={{ width: 145 }} options={[
          { value: 'SUCCEEDED', label: '执行成功' }, { value: 'FAILED', label: '执行失败' },
        ]} />
      <Button type="primary" onClick={search}>查询</Button>
    </Space>
    {error && <Alert type="error" showIcon title="记录列表读取失败" description={error} style={{ marginBottom: 16 }} />}
    <Table<DecisionRecord> rowKey="executionId" dataSource={data.records} loading={loading}
      scroll={{ x: 1050 }} columns={[
        { title: '记录时间', dataIndex: 'createdAt', width: 175,
          render: (value: string) => value?.replace('T', ' ') ?? '—' },
        { title: '场景', dataIndex: 'sceneCode', width: 170 },
        { title: '请求标识', dataIndex: 'requestId', ellipsis: true, width: 220 },
        { title: '固定版本 ID', dataIndex: 'versionId', width: 110 },
        { title: '状态', dataIndex: 'status', width: 105, render: (value: string) =>
          <Tag color={value === 'SUCCEEDED' ? 'green' : 'red'}>
            {value === 'SUCCEEDED' ? '成功' : '失败'}</Tag> },
        { title: '业务结论 / 错误码', width: 200,
          render: (_: unknown, record: DecisionRecord) => record.conclusion ?? record.errorCode ?? '未定义' },
        { title: '操作', width: 100, render: (_: unknown, record: DecisionRecord) =>
          <Button type="link" onClick={() => history.push(
            `/decision-records?executionId=${encodeURIComponent(record.executionId)}`)}>查看链路</Button> },
      ]}
      pagination={{ current: page, pageSize, total: data.total, showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 条`,
        onChange: (nextPage, nextSize) => {
          setPage(nextSize !== pageSize ? 1 : nextPage); setPageSize(nextSize)
        } }} />
    <DecisionRecordDetail record={detail} loading={detailLoading} error={detailError}
      open={openedId !== null} onClose={close} />
  </PageContainer>
}
