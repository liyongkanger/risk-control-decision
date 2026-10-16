import { PageContainer } from '@ant-design/pro-components'
import { Button, Form, Input, InputNumber, Modal, Select, Space, Table, Typography, message } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { createVariable, getVariable, listVariableCategories, listVariables, testVariable,
  updateVariable, updateVariableCategory } from '../../services/variableCenter'
import type { VariableCategory, VariableCategoryCode, VariablePage, VariableSourceType,
  VariableValueType, VariableVersion } from '../../services/variableCenter'
import { toCreateVariableInput, toVariableInput } from './variableForm'
import type { VariableFormValue } from './variableForm'

type FormValue = VariableFormValue

const demoSources = [
  { value: 'marketing://checkout', label: '营销样板：结算事实', parameters: 'uid, checkoutId, experimentId' },
  { value: 'marketing://budget', label: '营销样板：活动预算', parameters: 'uid, checkoutId, experimentId' },
]

/** 变量定义、不可变版本和试取值的后台入口。 */
export default function VariableCenterPage() {
  const [form] = Form.useForm<FormValue>()
  const [notice, holder] = message.useMessage()
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [searchText, setSearchText] = useState('')
  const [categoryCode, setCategoryCode] = useState<VariableCategoryCode | undefined>()
  const [sourceFilter, setSourceFilter] = useState<VariableSourceType | undefined>()
  const [valueFilter, setValueFilter] = useState<VariableValueType | undefined>()
  const [categories, setCategories] = useState<VariableCategory[]>([])
  const [categoriesLoading, setCategoriesLoading] = useState(false)
  const [categoriesError, setCategoriesError] = useState<string | null>(null)
  const latestListRequest = useRef(0)
  const [data, setData] = useState<VariablePage>({ records: [], total: 0, page: 1, pageSize: 10 })
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState<VariableVersion | null>(null)
  const [opened, setOpened] = useState(false)
  const [saving, setSaving] = useState(false)
  const [categoryEditing, setCategoryEditing] = useState<VariableVersion | null>(null)
  const [nextCategory, setNextCategory] = useState<VariableCategoryCode | undefined>()
  const [categorySaving, setCategorySaving] = useState(false)
  const [testing, setTesting] = useState<VariableVersion | null>(null)
  const sourceType = Form.useWatch('sourceType', form)
  const sourceUrl = Form.useWatch('url', form)
  const [testInput, setTestInput] = useState('{}')
  const [testResult, setTestResult] = useState<string | null>(null)
  const [testBusy, setTestBusy] = useState(false)

  const loadCategories = useCallback(async () => {
    setCategoriesLoading(true)
    setCategoriesError(null)
    try { setCategories(await listVariableCategories()) }
    catch (error) { setCategoriesError(error instanceof Error ? error.message : '分类读取失败') }
    finally { setCategoriesLoading(false) }
  }, [])
  useEffect(() => { void loadCategories() }, [loadCategories])

  const reload = useCallback(async () => {
    const request = ++latestListRequest.current
    setLoading(true)
    try {
      const result = await listVariables(keyword, page, size, { categoryCode,
        sourceType: sourceFilter, valueType: valueFilter })
      if (request !== latestListRequest.current) return
      if (page > 1 && result.records.length === 0) {
        setPage(Math.max(1, Math.min(page - 1, Math.ceil(result.total / size))))
      } else setData(result)
    }
    catch (error) { if (request === latestListRequest.current)
      notice.error(error instanceof Error ? error.message : '变量列表读取失败') }
    finally { if (request === latestListRequest.current) setLoading(false) }
  }, [keyword, page, size, categoryCode, sourceFilter, valueFilter, notice])
  useEffect(() => { void reload(); return () => { latestListRequest.current++ } }, [reload])

  const open = (variable: VariableVersion | null) => {
    setEditing(variable)
    form.setFieldsValue(variable ? { variableCode: variable.variableCode,
      variableName: variable.variableName, valueType: variable.valueType, url: variable.url ?? '',
      sourceType: variable.sourceType, timeoutMs: variable.timeoutMs,
      parameters: variable.parameterNames.join(', '),
      resultPath: variable.resultPath ?? '' }
      : { variableCode: '', variableName: '', valueType: 'STRING', sourceType: 'HTTP_GET', url: '', timeoutMs: 1000,
        parameters: '', resultPath: '', categoryCode: undefined })
    setOpened(true)
  }

  const save = async () => {
    const value = await form.validateFields()
    setSaving(true)
    try {
      const external = value.sourceType === 'HTTP_GET' || value.sourceType === 'DEMO_MARKETING'
      const names = external ? value.parameters.split(/[\s,，]+/).filter(Boolean) : []
      const dependencies = Object.fromEntries(await Promise.all(names.map(async (name) => {
        const version = await getVariable(name)
        return [name, version.id] as const
      })))
      if (editing) await updateVariable(editing.variableCode, editing.versionNumber,
        toVariableInput(value, dependencies))
      else if (value.categoryCode) await createVariable(toCreateVariableInput({ ...value,
        categoryCode: value.categoryCode }, dependencies))
      setOpened(false)
      notice.success(editing ? '已创建变量新版本' : '变量已创建')
      if (page === 1) await reload()
      else setPage(1)
    } catch (error) { notice.error(error instanceof Error ? error.message : '变量保存失败') }
    finally { setSaving(false) }
  }

  const saveCategory = async () => {
    if (!categoryEditing || !nextCategory) return
    setCategorySaving(true)
    try {
      await updateVariableCategory(categoryEditing.variableCode, nextCategory)
      setCategoryEditing(null)
      notice.success('分类已更新')
      await reload()
    } catch (error) { notice.error(error instanceof Error ? error.message : '分类更新失败') }
    finally { setCategorySaving(false) }
  }

  const runTest = async () => {
    if (!testing) return
    setTestBusy(true)
    try {
      const input = JSON.parse(testInput) as unknown
      if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('输入必须是 JSON 对象')
      const result = await testVariable(testing.variableCode, input as Record<string, unknown>)
      setTestResult(`${result.valueType} · ${String(result.value)} · 版本 ID ${result.versionId}`)
    } catch (error) { notice.error(error instanceof Error ? error.message : '试取值失败') }
    finally { setTestBusy(false) }
  }

  return <PageContainer title="变量中心">
    {holder}
    <Typography.Paragraph>变量可来自流程请求、本流程上游节点或外部只读来源。外部来源的参数名须引用已有变量码。流程发布后固定变量版本。</Typography.Paragraph>
    <Space style={{ marginBottom: 16 }}>
      <Input.Search aria-label="搜索变量" value={searchText} placeholder="变量码或名称"
        onChange={(event) => setSearchText(event.target.value)}
        onSearch={() => { latestListRequest.current++; setPage(1); setKeyword(searchText) }} />
      <Select aria-label="按分类筛选" placeholder="全部分类" allowClear style={{ width: 150 }}
        value={categoryCode} loading={categoriesLoading} options={categories.map(({ code, name }) => ({ value: code, label: name }))}
        onChange={(value: VariableCategoryCode | undefined) => {
          latestListRequest.current++; setCategoryCode(value); setPage(1)
        }} />
      <Select aria-label="按来源筛选" placeholder="全部来源" allowClear style={{ width: 170 }}
        value={sourceFilter} options={[
          { value: 'REQUEST_INPUT', label: '流程请求输入' },
          { value: 'FLOW_CONTEXT', label: '流程上下文' },
          { value: 'HTTP_GET', label: '只读 HTTP GET' },
          { value: 'DEMO_MARKETING', label: '营销样板模拟数据' },
        ]} onChange={(value: VariableSourceType | undefined) => {
          latestListRequest.current++; setSourceFilter(value); setPage(1)
        }} />
      <Select aria-label="按返回类型筛选" placeholder="全部返回类型" allowClear style={{ width: 160 }}
        value={valueFilter} options={['STRING', 'BOOLEAN', 'DECIMAL'].map((value) => ({ value, label: value }))}
        onChange={(value: VariableValueType | undefined) => {
          latestListRequest.current++; setValueFilter(value); setPage(1)
        }} />
      <Button type="primary" onClick={() => open(null)}>新建变量</Button>
    </Space>
    {categoriesError && <Typography.Paragraph type="danger">分类读取失败：{categoriesError}
      <Button type="link" onClick={() => void loadCategories()}>重试</Button>
    </Typography.Paragraph>}
    <Table<VariableVersion> rowKey="variableCode" loading={loading} dataSource={data.records}
      pagination={{ current: page, pageSize: size, total: data.total, showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 条`,
        onChange: (nextPage, nextSize) => {
          latestListRequest.current++; setPage(nextSize === size ? nextPage : 1); setSize(nextSize)
        } }}
      columns={[
        { title: '变量码', dataIndex: 'variableCode' },
        { title: '名称', dataIndex: 'variableName' },
        { title: '分类', dataIndex: 'categoryCode', render: (code: VariableCategoryCode) =>
          categories.find((category) => category.code === code)?.name ?? code },
        { title: '类型', dataIndex: 'valueType' },
        { title: '当前版本', dataIndex: 'versionNumber', render: (value: number) => `v${value}` },
        { title: '来源', dataIndex: 'sourceType' },
        { title: '取值路径', dataIndex: 'resultPath', render: (value: string) => value || '—' },
        { title: '操作', render: (_: unknown, row: VariableVersion) => <Space>
          <Button type="link" onClick={() => { setTesting(row); setTestResult(null);
            setTestInput(JSON.stringify(Object.fromEntries((row.sourceType === 'REQUEST_INPUT'
              || row.sourceType === 'FLOW_CONTEXT' ? [row.variableCode] : row.parameterNames)
              .map((name) => [name, ''])), null, 2)) }}>试取值</Button>
          <Button type="link" onClick={() => open(row)}>更新版本</Button>
          <Button type="link" onClick={() => { setCategoryEditing(row); setNextCategory(row.categoryCode) }}>修改分类</Button>
        </Space> },
      ]} />
    <Modal title={editing ? `更新 ${editing.variableCode} · v${editing.versionNumber}` : '新建变量'}
      open={opened} onCancel={() => setOpened(false)} onOk={() => void save()} confirmLoading={saving}>
      <Form form={form} layout="vertical">
        <Form.Item label="变量码" name="variableCode" rules={[{ required: true }]}><Input disabled={!!editing} /></Form.Item>
        <Form.Item label="变量名称" name="variableName" rules={[{ required: true }]}><Input /></Form.Item>
        {!editing && <Form.Item label="分类" name="categoryCode" rules={[{ required: true, message: '请选择分类' }]}>
          <Select placeholder="请选择分类" loading={categoriesLoading} options={categories.map(({ code, name }) =>
            ({ value: code, label: name }))} />
        </Form.Item>}
        <Form.Item label="返回类型" name="valueType" rules={[{ required: true }]}><Select options={
          ['STRING', 'BOOLEAN', 'DECIMAL'].map((value) => ({ value, label: value }))} /></Form.Item>
        <Form.Item label="来源类型" name="sourceType" rules={[{ required: true }]}><Select options={[
          { value: 'REQUEST_INPUT', label: '流程请求输入' },
          { value: 'FLOW_CONTEXT', label: '流程上下文' },
          { value: 'HTTP_GET', label: '只读 HTTP GET' },
          { value: 'DEMO_MARKETING', label: '营销样板模拟数据' },
        ]} onChange={(value: FormValue['sourceType']) => {
          if (value === 'DEMO_MARKETING') form.setFieldsValue({ valueType: 'DECIMAL',
            url: 'marketing://checkout', parameters: 'uid, checkoutId, experimentId', resultPath: 'orderAmount' })
          else form.setFieldsValue({ url: '', parameters: '', resultPath: '', timeoutMs: 1000 })
        }} /></Form.Item>
        {(sourceType === 'REQUEST_INPUT' || sourceType === 'FLOW_CONTEXT') && <Typography.Paragraph type="secondary">
          {sourceType === 'REQUEST_INPUT' ? '变量码必须与流程请求输入字段名一致。' : '在流程画布中绑定生产节点及结果路径。'}
        </Typography.Paragraph>}
        {sourceType === 'DEMO_MARKETING' ? <Form.Item label="样板数据来源" name="url" rules={[{ required: true }]}>
          <Select options={demoSources} onChange={(value: string) => {
            const selected = demoSources.find((source) => source.value === value)
            form.setFieldsValue({ parameters: selected?.parameters ?? '',
              resultPath: value === 'marketing://budget' ? 'remainingBudget' : 'orderAmount' })
          }} /></Form.Item>
          : sourceType === 'HTTP_GET' && <Form.Item label="固定 GET URL" name="url" rules={[{ required: true }]}><Input placeholder="http://localhost:9199/lookup" /></Form.Item>}
        {(sourceType === 'HTTP_GET' || sourceType === 'DEMO_MARKETING') && <>
          <Form.Item label="超时（毫秒）" name="timeoutMs" rules={[{ required: true }]}><InputNumber min={100} max={10000} style={{ width: '100%' }} /></Form.Item>
          <Form.Item label="依赖变量码（逗号分隔）" name="parameters"><Input disabled={sourceType === 'DEMO_MARKETING'} placeholder="userId" /></Form.Item>
          <Typography.Paragraph type="secondary">保存时固定这些变量的当前版本 ID。
            {editing && Object.keys(editing.dependencyVersionIds ?? {}).length > 0
              && ` 现有依赖：${Object.entries(editing.dependencyVersionIds)
                .map(([code, id]) => `${code} #${id}`).join('、')}`}</Typography.Paragraph>
        </>}
        {sourceType === 'DEMO_MARKETING' ? <Form.Item label="样板字段" name="resultPath" rules={[{ required: true }]}>
          <Select options={(sourceUrl === 'marketing://budget' ? ['remainingBudget']
            : ['orderAmount', 'marginAfterMaxOffer']).map((value) => ({ value, label: value }))} />
        </Form.Item> : sourceType === 'HTTP_GET' && <Form.Item label="JSON 取值路径" name="resultPath" rules={[{ required: true }]}>
          <Input placeholder="data.amount" /></Form.Item>}
      </Form>
    </Modal>
    <Modal title={categoryEditing ? `修改分类 · ${categoryEditing.variableCode}` : '修改分类'}
      open={!!categoryEditing} onCancel={() => setCategoryEditing(null)} onOk={() => void saveCategory()}
      okButtonProps={{ disabled: !nextCategory }} confirmLoading={categorySaving}>
      <Select aria-label="变量分类" style={{ width: '100%' }} value={nextCategory}
        loading={categoriesLoading} options={categories.map(({ code, name }) => ({ value: code, label: name }))}
        onChange={(value: VariableCategoryCode) => setNextCategory(value)} />
    </Modal>
    <Modal title={testing ? `试取值 · ${testing.variableCode}` : '试取值'} open={!!testing}
      onCancel={() => setTesting(null)} onOk={() => void runTest()} confirmLoading={testBusy} okText="执行">
      <Typography.Paragraph>输入参数 JSON</Typography.Paragraph>
      <Input.TextArea aria-label="试取值输入 JSON" value={testInput} rows={5}
        onChange={(event) => setTestInput(event.target.value)} />
      {testResult && <Typography.Paragraph style={{ marginTop: 12 }}>{testResult}</Typography.Paragraph>}
    </Modal>
  </PageContainer>
}
