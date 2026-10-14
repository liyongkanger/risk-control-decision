import { history } from '@umijs/max'
import { PageContainer } from '@ant-design/pro-components'
import { Button, Form, Input, Modal, Space, Table, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useState } from 'react'
import { createFlow, listFlows } from '../../services/flow'
import type { FlowAsset, FlowPage } from '../../services/flow'

type FlowForm = { flowCode: string; flowName: string }

/** 独立流程资产的分页管理与设计入口。 */
export default function FlowAssetPage() {
  const [form] = Form.useForm<FlowForm>()
  const [result, setResult] = useState<FlowPage>({ records: [], total: 0, page: 1, pageSize: 10 })
  const [keyword, setKeyword] = useState('')
  const [query, setQuery] = useState('')
  const [pageNumber, setPageNumber] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [notice, noticeHolder] = message.useMessage()

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setResult(await listFlows(query, pageNumber, pageSize))
    } catch (error) {
      notice.error(error instanceof Error ? error.message : '流程列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [query, pageNumber, pageSize, notice])

  useEffect(() => { void reload() }, [reload])

  const create = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      await createFlow(values)
      notice.success('流程已创建')
      setModalOpen(false)
      form.resetFields()
      setPageNumber(1)
      if (pageNumber === 1) await reload()
    } catch (error) {
      notice.error(error instanceof Error ? error.message : '创建流程失败')
    } finally {
      setSaving(false)
    }
  }

  const columns: ColumnsType<FlowAsset> = [
    { title: '流程码', dataIndex: 'flowCode', width: 190 },
    { title: '流程名称', dataIndex: 'flowName', width: 190 },
    { title: '类型', dataIndex: 'flowType', width: 110,
      render: (value: FlowAsset['flowType']) => value === 'DECISION' ? '决策流程' : value },
    { title: '当前版本', dataIndex: 'publishedVersionNumber', width: 110,
      render: (value: number | null) => value == null ? '未发布' : `v${value}` },
    { title: '更新时间', dataIndex: 'updatetime', width: 170, responsive: ['lg'],
      render: (value: string) => value ? value.replace('T', ' ').slice(0, 19) : '—' },
    { title: '操作', width: 130, render: (_value: unknown, flow: FlowAsset) => <Button type="link"
      onClick={() => history.push(`/flows/${encodeURIComponent(flow.flowCode)}/editor`)}>设计流程</Button> },
  ]

  return <PageContainer title="流程资产">
    <div className="scene-list-page">
      {noticeHolder}
      <div className="scene-list-heading">
        <Typography.Text type="secondary">流程可独立发布，并作为多个场景的入口复用。</Typography.Text>
        <Button type="primary" onClick={() => { form.resetFields(); setModalOpen(true) }}>新建流程</Button>
      </div>
      <Space className="scene-list-search">
        <Input.Search placeholder="按流程码或名称搜索" value={keyword} allowClear
          onChange={(event) => setKeyword(event.target.value)}
          onSearch={() => { setPageNumber(1); setQuery(keyword.trim()) }} />
      </Space>
      <Table<FlowAsset> rowKey="flowCode" loading={loading} dataSource={result.records} columns={columns}
        pagination={{ current: pageNumber, pageSize, total: result.total, showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 条`,
          onChange: (nextPage, nextPageSize) => {
            setPageNumber(nextPageSize === pageSize ? nextPage : 1)
            setPageSize(nextPageSize)
          } }} locale={{ emptyText: '暂无流程，先新建一个流程' }} />
      <Modal title="新建流程" open={modalOpen} confirmLoading={saving}
        onCancel={() => setModalOpen(false)} onOk={() => void create()} okText="创建">
        <Form<FlowForm> form={form} layout="vertical">
          <Form.Item name="flowCode" label="流程码" rules={[
            { required: true, message: '请输入流程码' },
            { pattern: /^[A-Za-z][A-Za-z0-9_-]{0,63}$/, message: '以字母开头，仅支持字母、数字、下划线和连字符，最长 64 位' },
          ]}><Input maxLength={64} placeholder="例如 sharedRisk" /></Form.Item>
          <Form.Item name="flowName" label="流程名称"
            rules={[{ required: true, whitespace: true, message: '请输入流程名称' }]}>
            <Input maxLength={128} placeholder="例如 共享风险判断" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  </PageContainer>
}
