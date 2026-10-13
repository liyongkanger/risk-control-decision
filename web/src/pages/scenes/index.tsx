import { history } from '@umijs/max'
import { PageContainer } from '@ant-design/pro-components'
import { Button, Form, Input, Modal, Select, Space, Table, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useState } from 'react'
import { bindSceneFlow, createScene, listScenes, updateScene } from '../../services/scene'
import type { Scene, ScenePage } from '../../services/scene'
import { listFlows } from '../../services/flow'
import type { FlowAsset } from '../../services/flow'

type Props = { selectionMode?: boolean; onDesign?: (sceneCode: string) => void }
type SceneForm = { sceneCode?: string; sceneName: string; description?: string }

/** 场景管理及流程设计入口，共用同一份后端场景数据。 */
export default function SceneListPage({ selectionMode = false, onDesign }: Props) {
  const [form] = Form.useForm<SceneForm>()
  const [page, setPage] = useState<ScenePage>({ records: [], total: 0, page: 1, pageSize: 20 })
  const [keyword, setKeyword] = useState('')
  const [query, setQuery] = useState('')
  const [pageNumber, setPageNumber] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState<Scene | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [binding, setBinding] = useState<Scene | null>(null)
  const [bindingFlowCode, setBindingFlowCode] = useState<string | null>(null)
  const [bindingSaving, setBindingSaving] = useState(false)
  const [flowOptions, setFlowOptions] = useState<FlowAsset[]>([])
  const [flowsLoading, setFlowsLoading] = useState(false)
  const [notice, noticeHolder] = message.useMessage()

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setPage(await listScenes(query, pageNumber, pageSize))
    } catch (error) {
      notice.error(error instanceof Error ? error.message : '场景列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [query, pageNumber, pageSize, notice])

  useEffect(() => { void reload() }, [reload])

  const design = (sceneCode: string) => {
    if (onDesign) onDesign(sceneCode)
    else history.push(`/scenes/${encodeURIComponent(sceneCode)}/flow`)
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (scene: Scene) => {
    setEditing(scene)
    form.setFieldsValue({ sceneCode: scene.sceneCode, sceneName: scene.sceneName,
      description: scene.description ?? '' })
    setModalOpen(true)
  }

  const save = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) {
        await updateScene(editing.sceneCode, { sceneName: values.sceneName, description: values.description ?? null })
        notice.success('场景已更新')
      } else {
        await createScene({ sceneCode: values.sceneCode ?? '', sceneName: values.sceneName,
          description: values.description ?? null })
        notice.success('场景已创建')
      }
      setModalOpen(false)
      await reload()
    } catch (error) {
      notice.error(error instanceof Error ? error.message : '保存场景失败')
    } finally {
      setSaving(false)
    }
  }

  const searchFlows = async (value: string) => {
    setFlowsLoading(true)
    try {
      setFlowOptions((await listFlows(value.trim(), 1, 20)).records)
    } catch (error) {
      notice.error(error instanceof Error ? error.message : '流程列表加载失败')
    } finally {
      setFlowsLoading(false)
    }
  }

  const openBind = (scene: Scene) => {
    setBinding(scene)
    setBindingFlowCode(scene.flowCode)
    void searchFlows('')
  }

  const bind = async () => {
    if (!binding || !bindingFlowCode) return
    setBindingSaving(true)
    try {
      await bindSceneFlow(binding.sceneCode, bindingFlowCode)
      notice.success('入口流程已绑定')
      setBinding(null)
      await reload()
    } catch (error) {
      notice.error(error instanceof Error ? error.message : '绑定流程失败')
    } finally {
      setBindingSaving(false)
    }
  }

  const columns: ColumnsType<Scene> = [
    { title: '场景码', dataIndex: 'sceneCode', width: 155 },
    { title: '场景名称', dataIndex: 'sceneName', width: 160 },
    { title: '入口流程', dataIndex: 'flowCode', width: 160,
      render: (value: string) => value || '—' },
    { title: '描述', dataIndex: 'description', responsive: ['xl'], ellipsis: true,
      render: (value: string | null) => value || '—' },
    { title: '当前版本', dataIndex: 'publishedVersionNumber', width: 90,
      render: (value: number | null) => value == null ? '未发布' : `v${value}` },
    { title: '更新时间', dataIndex: 'updatetime', responsive: ['lg'], width: 170,
      render: (value: string) => value ? value.replace('T', ' ').slice(0, 19) : '—' },
    { title: '操作', width: 235, render: (_value: unknown, scene: Scene) => <Space>
      <Button type="link" onClick={() => design(scene.sceneCode)}>设计流程</Button>
      {!selectionMode && <Button type="link" onClick={() => openEdit(scene)}>编辑</Button>}
      {!selectionMode && <Button type="link" onClick={() => openBind(scene)}>绑定流程</Button>}
    </Space> },
  ]

  return <PageContainer title={selectionMode ? '选择场景设计流程' : '场景管理'}>
    <div className="scene-list-page">
    {noticeHolder}
    <div className="scene-list-heading">
      <Typography.Text type="secondary">场景码是决策流程的稳定标识，创建后不可修改。</Typography.Text>
      <Button type="primary" onClick={openCreate}>新建场景</Button>
    </div>
    <Space className="scene-list-search">
      <Input.Search placeholder="按场景码或名称搜索" value={keyword} allowClear
        onChange={(event) => setKeyword(event.target.value)}
        onSearch={() => { setPageNumber(1); setQuery(keyword.trim()) }} />
    </Space>
    <Table<Scene> rowKey="sceneCode" loading={loading} dataSource={page.records} columns={columns}
      pagination={{ current: pageNumber, pageSize, total: page.total, showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 条`,
        onChange: (nextPage, nextPageSize) => {
          setPageNumber(nextPageSize === pageSize ? nextPage : 1)
          setPageSize(nextPageSize)
        } }} locale={{ emptyText: '暂无场景，先新建一个场景' }} />
    <Modal title={editing ? '编辑场景' : '新建场景'} open={modalOpen} confirmLoading={saving}
      onCancel={() => setModalOpen(false)} onOk={() => void save()} okText="保存">
      <Form<SceneForm> form={form} layout="vertical">
        <Form.Item name="sceneCode" label="场景码" rules={editing ? [] : [
          { required: true, message: '请输入场景码' },
          { pattern: /^[A-Za-z][A-Za-z0-9_-]{0,63}$/, message: '以字母开头，仅支持字母、数字、下划线和连字符，最长 64 位' },
        ]}>
          <Input disabled={!!editing} maxLength={64} placeholder="例如 paymentRisk" />
        </Form.Item>
        <Form.Item name="sceneName" label="场景名称" rules={[{ required: true, whitespace: true, message: '请输入场景名称' }]}>
          <Input maxLength={128} placeholder="例如 支付风控" />
        </Form.Item>
        <Form.Item name="description" label="场景描述"><Input.TextArea rows={3} maxLength={512} /></Form.Item>
      </Form>
    </Modal>
    <Modal title={`绑定入口流程 · ${binding?.sceneName ?? ''}`} open={!!binding}
      confirmLoading={bindingSaving} onCancel={() => setBinding(null)} onOk={() => void bind()} okText="绑定">
      <Typography.Paragraph type="secondary">绑定时选中流程当前发布版本；流程后续发布不会自动切换该场景。</Typography.Paragraph>
      <Select showSearch filterOption={false} loading={flowsLoading} value={bindingFlowCode}
        onSearch={(value) => void searchFlows(value)} onChange={setBindingFlowCode}
        placeholder="按流程码或名称搜索" style={{ width: '100%' }}
        options={[
          ...(bindingFlowCode && !flowOptions.some((flow) => flow.flowCode === bindingFlowCode)
            ? [{ value: bindingFlowCode, label: bindingFlowCode }] : []),
          ...flowOptions.map((flow) => ({ value: flow.flowCode,
            label: `${flow.flowName} (${flow.flowCode}) · ${flow.publishedVersionNumber == null ? '未发布' : `v${flow.publishedVersionNumber}`}` })),
        ]} />
    </Modal>
    </div>
  </PageContainer>
}
