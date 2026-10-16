import { Alert, Button, Input, Modal, Select, Space, Table } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { listVariableCategories, listVariables } from '../../services/variableCenter'
import type { VariableCategory, VariableCategoryCode, VariablePage,
  VariableVersion } from '../../services/variableCenter'

type Props = { open: boolean; selectedVersionId?: number; onCancel: () => void
  onChoose: (variable: VariableVersion) => void }

/** 从变量中心分页搜索当前变量版本，供规则输入直接选择。 */
export default function VariablePicker({ open, selectedVersionId, onCancel, onChoose }: Props) {
  const [query, setQuery] = useState('')
  const [keyword, setKeyword] = useState('')
  const [categoryCode, setCategoryCode] = useState<VariableCategoryCode | undefined>()
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [refreshKey, setRefreshKey] = useState(0)
  const [categories, setCategories] = useState<VariableCategory[]>([])
  const [data, setData] = useState<{ queryKey: string; page: VariablePage } | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const requestSequence = useRef(0)
  const queryKey = JSON.stringify([keyword, categoryCode, page, pageSize, refreshKey])
  const visiblePage = data?.queryKey === queryKey && !error && !loading ? data.page : null

  useEffect(() => {
    if (!open) return
    let active = true
    void listVariableCategories().then((value) => { if (active) setCategories(value) })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : '分类读取失败') })
    return () => { active = false }
  }, [open])

  useEffect(() => {
    if (!open) return
    const request = ++requestSequence.current
    setLoading(true)
    setError(null)
    setData(null)
    void listVariables(keyword, page, pageSize, { categoryCode })
      .then((value) => {
        if (request === requestSequence.current) setData({ queryKey, page: value })
      })
      .catch((cause) => {
        if (request === requestSequence.current) {
          setData(null)
          setError(cause instanceof Error ? cause.message : '变量读取失败')
        }
      })
      .finally(() => { if (request === requestSequence.current) setLoading(false) })
    return () => { requestSequence.current++ }
  }, [open, keyword, categoryCode, page, pageSize, refreshKey])

  return <Modal title="从变量中心选择变量" open={open} width={940} footer={null} onCancel={onCancel}>
    <Space wrap style={{ marginBottom: 16 }}>
      <Input.Search aria-label="搜索变量中心变量" placeholder="变量码或名称" value={query}
        onChange={(event) => setQuery(event.target.value)}
        onSearch={() => {
          requestSequence.current++; setPage(1); setKeyword(query.trim()); setRefreshKey((value) => value + 1)
        }} />
      <Select aria-label="按变量分类筛选" placeholder="全部分类" allowClear style={{ width: 170 }}
        value={categoryCode} options={categories.map(({ code, name }) => ({ value: code, label: name }))}
        onChange={(value: VariableCategoryCode | undefined) => {
          requestSequence.current++; setPage(1); setCategoryCode(value)
        }} />
    </Space>
    {error && <Alert type="error" showIcon title="变量读取失败" description={error}
      style={{ marginBottom: 12 }} />}
    <Table<VariableVersion> rowKey="variableCode" size="small" loading={loading}
      dataSource={visiblePage?.records ?? []}
      pagination={{ current: page, pageSize, total: visiblePage?.total ?? 0, showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 条`,
        onChange: (nextPage, nextSize) => {
          requestSequence.current++; setPage(nextSize === pageSize ? nextPage : 1)
          setPageSize(nextSize)
        } }}
      columns={[
        { title: '变量码', dataIndex: 'variableCode' },
        { title: '名称', dataIndex: 'variableName' },
        { title: '分类', dataIndex: 'categoryCode', render: (code: VariableCategoryCode) =>
          categories.find((category) => category.code === code)?.name ?? code },
        { title: '类型', dataIndex: 'valueType' },
        { title: '来源', dataIndex: 'sourceType' },
        { title: '版本', dataIndex: 'versionNumber', render: (value: number) => `v${value}` },
        { title: '操作', render: (_: unknown, variable: VariableVersion) =>
          <Button type="link" disabled={loading || !!error || variable.id === selectedVersionId}
            onClick={() => onChoose(variable)}>
            {variable.id === selectedVersionId ? '已选择' : '选择变量'}
          </Button> },
      ]} />
  </Modal>
}
