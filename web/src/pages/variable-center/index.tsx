import { PageContainer } from '@ant-design/pro-components'
import { Button, Form, Input, InputNumber, Modal, Select, Space, Table, Typography, message } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { createVariable, listVariables, testVariable, updateVariable } from '../../services/variableCenter'
import type { VariablePage, VariableValueType, VariableVersion } from '../../services/variableCenter'

type FormValue = {
  variableCode: string; variableName: string; valueType: VariableValueType
  url: string; timeoutMs: number; parameters: string; resultPath: string
}

/** 变量定义、不可变版本和试取值的后台入口。 */
export default function VariableCenterPage() {
  const [form] = Form.useForm<FormValue>()
  const [notice, holder] = message.useMessage()
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(10)
  const [keyword, setKeyword] = useState('')
  const [searchText, setSearchText] = useState('')
  const [data, setData] = useState<VariablePage>({ records: [], total: 0, page: 1, pageSize: 10 })
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState<VariableVersion | null>(null)
  const [opened, setOpened] = useState(false)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState<VariableVersion | null>(null)
  const [testInput, setTestInput] = useState('{}')
  const [testResult, setTestResult] = useState<string | null>(null)
  const [testBusy, setTestBusy] = useState(false)

  const reload = useCallback(async () => {
    setLoading(true)
    try { setData(await listVariables(keyword, page, size)) }
    catch (error) { notice.error(error instanceof Error ? error.message : '变量列表读取失败') }
    finally { setLoading(false) }
  }, [keyword, page, size, notice])
  useEffect(() => { void reload() }, [reload])

  const open = (variable: VariableVersion | null) => {
    setEditing(variable)
    form.setFieldsValue(variable ? { variableCode: variable.variableCode,
      variableName: variable.variableName, valueType: variable.valueType, url: variable.url,
      timeoutMs: variable.timeoutMs, parameters: variable.parameterNames.join(', '),
      resultPath: variable.resultPath }
      : { variableCode: '', variableName: '', valueType: 'STRING', url: '', timeoutMs: 1000,
        parameters: '', resultPath: '' })
    setOpened(true)
  }

  const save = async () => {
    const value = await form.validateFields()
    setSaving(true)
    try {
      const input = { variableCode: value.variableCode, variableName: value.variableName,
        valueType: value.valueType, sourceType: 'HTTP_GET' as const,
        url: value.url, timeoutMs: value.timeoutMs, resultPath: value.resultPath,
        parameterNames: value.parameters.split(/[\s,，]+/).map((item) => item.trim()).filter(Boolean) }
      if (editing) await updateVariable(editing.variableCode, editing.versionNumber, input)
      else await createVariable(input)
      setOpened(false)
      notice.success(editing ? '已创建变量新版本' : '变量已创建')
      if (page === 1) await reload()
      else setPage(1)
    } catch (error) { notice.error(error instanceof Error ? error.message : '变量保存失败') }
    finally { setSaving(false) }
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
    <Typography.Paragraph>变量中心将只读 HTTP 响应转换为单个强类型值。流程发布后固定变量版本。</Typography.Paragraph>
    <Space style={{ marginBottom: 16 }}>
      <Input.Search aria-label="搜索变量" value={searchText} placeholder="变量码或名称"
        onChange={(event) => setSearchText(event.target.value)}
        onSearch={() => { setPage(1); setKeyword(searchText) }} />
      <Button type="primary" onClick={() => open(null)}>新建变量</Button>
    </Space>
    <Table<VariableVersion> rowKey="variableCode" loading={loading} dataSource={data.records}
      pagination={{ current: page, pageSize: size, total: data.total, showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 条`,
        onChange: (nextPage, nextSize) => { setPage(nextSize === size ? nextPage : 1); setSize(nextSize) } }}
      columns={[
        { title: '变量码', dataIndex: 'variableCode' },
        { title: '名称', dataIndex: 'variableName' },
        { title: '类型', dataIndex: 'valueType' },
        { title: '当前版本', dataIndex: 'versionNumber', render: (value: number) => `v${value}` },
        { title: '来源', dataIndex: 'sourceType' },
        { title: '取值路径', dataIndex: 'resultPath' },
        { title: '操作', render: (_: unknown, row: VariableVersion) => <Space>
          <Button type="link" onClick={() => { setTesting(row); setTestResult(null);
            setTestInput(JSON.stringify(Object.fromEntries(row.parameterNames.map((name) => [name, ''])), null, 2)) }}>试取值</Button>
          <Button type="link" onClick={() => open(row)}>更新版本</Button>
        </Space> },
      ]} />
    <Modal title={editing ? `更新 ${editing.variableCode} · v${editing.versionNumber}` : '新建变量'}
      open={opened} onCancel={() => setOpened(false)} onOk={() => void save()} confirmLoading={saving}>
      <Form form={form} layout="vertical">
        <Form.Item label="变量码" name="variableCode" rules={[{ required: true }]}><Input disabled={!!editing} /></Form.Item>
        <Form.Item label="变量名称" name="variableName" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item label="返回类型" name="valueType" rules={[{ required: true }]}><Select options={
          ['STRING', 'BOOLEAN', 'DECIMAL'].map((value) => ({ value, label: value }))} /></Form.Item>
        <Form.Item label="固定 GET URL" name="url" rules={[{ required: true }]}><Input placeholder="http://localhost:9199/lookup" /></Form.Item>
        <Form.Item label="超时（毫秒）" name="timeoutMs" rules={[{ required: true }]}><InputNumber min={100} max={10000} style={{ width: '100%' }} /></Form.Item>
        <Form.Item label="查询参数名（逗号分隔）" name="parameters"><Input placeholder="userId" /></Form.Item>
        <Form.Item label="JSON 取值路径" name="resultPath" rules={[{ required: true }]}><Input placeholder="data.amount" /></Form.Item>
      </Form>
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
