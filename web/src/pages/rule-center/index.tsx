import { PageContainer } from '@ant-design/pro-components'
import { useLocation } from '@umijs/max'
import { Button, Card, Input, InputNumber, Modal, Select, Space, Table, Typography, message } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { createRuleSet, getRuleSet, getRuleSetVersion, listRuleSets, publishRuleSet, testRuleSet,
  testRuleSetVersion, updateRuleDraft } from '../../services/ruleCenter'
import type { RuleCondition, RuleDefinition, RuleOutcome, RuleSet, RuleSetPage, RuleSetSummary, RuleValueType } from '../../services/ruleCenter'
import { parseRuleTestInput } from './ruleInput'

const emptyDefinition = (): RuleDefinition => ({
  inputs: [{ name: 'value', type: 'DECIMAL' }], resultCodes: ['PASS', 'REJECT'], rows: [],
  defaultResult: { resultCode: 'PASS', reasonCode: 'DEFAULT', reason: '符合规则' },
})
const operators = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE'] as const
const typeOptions = ['STRING', 'BOOLEAN', 'DECIMAL'] as const

/** 编辑并发布有版本的首命中规则表。 */
export default function RuleCenterPage() {
  const location = useLocation()
  const initialKeyword = new URLSearchParams(location.search).get('keyword') ?? ''
  const [notice, holder] = message.useMessage()
  const [keyword, setKeyword] = useState(initialKeyword)
  const [searchText, setSearchText] = useState(initialKeyword)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(10)
  const [data, setData] = useState<RuleSetPage>({ records: [], total: 0, page: 1, pageSize: 10 })
  const [loading, setLoading] = useState(false)
  const [opened, setOpened] = useState(false)
  const [existing, setExisting] = useState<RuleSet | null>(null)
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [definition, setDefinition] = useState<RuleDefinition>(emptyDefinition)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [sample, setSample] = useState('{"value":"100"}')
  const [testVersionId, setTestVersionId] = useState<number | null>(null)
  const [result, setResult] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try { setData(await listRuleSets(keyword, page, size)) }
    catch (error) { notice.error(error instanceof Error ? error.message : '规则集读取失败') }
    finally { setLoading(false) }
  }, [keyword, page, size, notice])
  useEffect(() => { void reload() }, [reload])

  const change = (next: RuleDefinition) => { setDefinition(next); setDirty(true); setResult(null) }
  const newRule = () => {
    setExisting(null); setCode(''); setName(''); setDefinition(emptyDefinition());
    setDirty(true); setResult(null); setSample('{"value":"100"}'); setTestVersionId(null); setOpened(true)
  }
  const openRule = async (item: RuleSetSummary) => {
    try {
      const current = await getRuleSet(item.code)
      setExisting(current); setCode(current.code); setName(current.name)
      setDefinition(current.definition); setDirty(false); setResult(null); setOpened(true)
      setTestVersionId(current.currentVersionId)
      setSample(JSON.stringify(Object.fromEntries(current.definition.inputs.map((input) =>
        [input.name, input.type === 'DECIMAL' ? '0' : input.type === 'BOOLEAN' ? false : ''])), null, 2))
    } catch (error) { notice.error(error instanceof Error ? error.message : '规则详情读取失败') }
  }
  const save = async () => {
    setBusy(true)
    try {
      const next = existing
        ? await updateRuleDraft(code, existing.draftRevision, definition)
        : await createRuleSet(code, name, definition)
      setExisting(next); setDirty(false); notice.success(`已保存草稿 r${next.draftRevision}`)
      await reload()
    } catch (error) { notice.error(error instanceof Error ? error.message : '草稿保存失败') }
    finally { setBusy(false) }
  }
  const publish = async () => {
    if (!existing || dirty) { notice.error('请先保存当前草稿'); return }
    setBusy(true)
    try {
      const version = await publishRuleSet(code, existing.draftRevision)
      const latest = await getRuleSet(code)
      setExisting(latest); setTestVersionId(version.id)
      notice.success(`已发布 v${version.versionNumber} · ID ${version.id}`)
      await reload()
    } catch (error) { notice.error(error instanceof Error ? error.message : '规则发布失败') }
    finally { setBusy(false) }
  }
  const runTest = async () => {
    if (!existing || dirty) { notice.error('请先保存当前草稿再试算'); return }
    setBusy(true)
    try {
      const input = parseRuleTestInput(sample, definition)
      const value = await testRuleSet(code, input)
      setResult(`${value.resultCode} · ${value.reasonCode} · ${value.reason} · ${value.matchedRuleId ?? '兜底'}`)
    } catch (error) { notice.error(error instanceof Error ? error.message : '规则试算失败') }
    finally { setBusy(false) }
  }
  const runVersionTest = async () => {
    if (!existing || !testVersionId) { notice.error('请选择已发布版本 ID'); return }
    setBusy(true)
    try {
      const version = await getRuleSetVersion(testVersionId)
      if (version.code !== code) throw new Error('该版本不属于当前规则集')
      const input = parseRuleTestInput(sample, version.definition)
      const value = await testRuleSetVersion(code, testVersionId, input)
      setResult(`v${version.versionNumber} · ${value.resultCode} · ${value.reasonCode} · ${value.reason} · ${value.matchedRuleId ?? '兜底'}`)
    } catch (error) { notice.error(error instanceof Error ? error.message : '发布版本试算失败') }
    finally { setBusy(false) }
  }
  const updateInput = (index: number, patch: Partial<RuleDefinition['inputs'][number]>) => change({
    ...definition, inputs: definition.inputs.map((item, i) => i === index ? { ...item, ...patch } : item),
  })
  const updateRow = (index: number, patch: Partial<RuleDefinition['rows'][number]>) => change({
    ...definition, rows: definition.rows.map((item, i) => i === index ? { ...item, ...patch } : item),
  })
  const updateCondition = (rowIndex: number, conditionIndex: number, patch: Partial<RuleCondition>) => {
    const row = definition.rows[rowIndex]
    updateRow(rowIndex, { conditions: row.conditions.map((item, i) =>
      i === conditionIndex ? { ...item, ...patch } : item) })
  }
  const editOutcome = (outcome: RuleOutcome, patch: Partial<RuleOutcome>) => ({ ...outcome, ...patch })

  return <PageContainer title="规则中心">
    {holder}
    <Typography.Paragraph>规则集按优先级命中第一行；流程引用固定发布版本。变量取值仍由变量中心负责。</Typography.Paragraph>
    <Space style={{ marginBottom: 16 }}>
      <Input.Search aria-label="搜索规则集" value={searchText} placeholder="规则集码或名称"
        onChange={(event) => setSearchText(event.target.value)}
        onSearch={() => { setPage(1); setKeyword(searchText) }} />
      <Button type="primary" onClick={newRule}>新建规则集</Button>
    </Space>
    <Table<RuleSetSummary> rowKey="code" loading={loading} dataSource={data.records}
      pagination={{ current: page, pageSize: size, total: data.total, showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 条`,
        onChange: (nextPage, nextSize) => { setPage(nextSize === size ? nextPage : 1); setSize(nextSize) } }}
      columns={[{ title: '规则集码', dataIndex: 'code' }, { title: '名称', dataIndex: 'name' },
        { title: '草稿修订号', dataIndex: 'draftRevision' },
        { title: '当前发布版本 ID', dataIndex: 'currentVersionId', render: (value: number | null) => value ?? '未发布' },
        { title: '操作', render: (_: unknown, item: RuleSetSummary) =>
          <Button type="link" onClick={() => void openRule(item)}>编辑 / 试算</Button> }]} />
    <Modal title={existing ? `规则集 ${code} · 草稿 r${existing.draftRevision}` : '新建规则集'}
      open={opened} width={1000} footer={null} onCancel={() => { if (dirty && !window.confirm('草稿尚未保存，确定关闭吗？')) return; setOpened(false) }}>
      <div style={{ maxHeight: '68vh', overflowY: 'auto', paddingRight: 8 }}>
        <Space wrap style={{ marginBottom: 16 }}>
          <label>规则集码 <Input aria-label="规则集码" value={code} disabled={!!existing} onChange={(e) => setCode(e.target.value)} /></label>
          <label>名称 <Input aria-label="规则集名称" value={name} disabled={!!existing} onChange={(e) => setName(e.target.value)} /></label>
          <span>发布版本 ID：{existing?.currentVersionId ?? '未发布'}</span>
        </Space>
        <Card size="small" title="输入字段" style={{ marginBottom: 12 }} extra={<Button onClick={() => change({ ...definition,
          inputs: [...definition.inputs, { name: '', type: 'STRING' }] })}>添加输入</Button>}>
          {definition.inputs.map((input, index) => <Space key={index} wrap style={{ display: 'flex', marginBottom: 8 }}>
            <Input aria-label={`输入字段 ${index + 1}`} placeholder="字段名" value={input.name}
              onChange={(e) => updateInput(index, { name: e.target.value })} />
            <Select aria-label={`输入类型 ${index + 1}`} value={input.type} style={{ width: 130 }}
              options={typeOptions.map((value) => ({ value, label: value }))}
              onChange={(value: RuleValueType) => updateInput(index, { type: value })} />
            <Button danger onClick={() => change({ ...definition,
              inputs: definition.inputs.filter((_, i) => i !== index) })}>删除</Button>
          </Space>)}
        </Card>
        <Card size="small" title="业务结果码" style={{ marginBottom: 12 }}>
          <Input aria-label="结果码列表" value={definition.resultCodes.join(', ')}
            placeholder="PASS, REJECT" onChange={(e) => change({ ...definition,
              resultCodes: e.target.value.split(/[,，]/).map((item) => item.trim()).filter(Boolean) })} />
        </Card>
        <Card size="small" title="规则行（优先级小的先判断）" style={{ marginBottom: 12 }}
          extra={<Button onClick={() => change({ ...definition, rows: [...definition.rows, {
            id: '', priority: (definition.rows.at(-1)?.priority ?? 0) + 10,
            conditions: [{ input: definition.inputs[0]?.name ?? '', operator: 'EQ', expected: '' }],
            result: { resultCode: definition.resultCodes[0] ?? '', reasonCode: '', reason: '' },
          }] })}>添加规则行</Button>}>
          {definition.rows.map((row, index) => <Card key={index} size="small" style={{ marginBottom: 10 }}
            title={<Space><Input aria-label={`规则行标识 ${index + 1}`} placeholder="规则行标识" value={row.id}
              onChange={(e) => updateRow(index, { id: e.target.value })} />
              <InputNumber aria-label={`优先级 ${index + 1}`} min={0} value={row.priority}
                onChange={(value) => updateRow(index, { priority: value ?? 0 })} /></Space>}
            extra={<Button danger onClick={() => change({ ...definition,
              rows: definition.rows.filter((_, i) => i !== index) })}>删除行</Button>}>
            {row.conditions.map((condition, ci) => {
              const type = definition.inputs.find((input) => input.name === condition.input)?.type ?? 'STRING'
              return <Space key={ci} wrap style={{ display: 'flex', marginBottom: 8 }}>
                <Select aria-label={`条件字段 ${index + 1}-${ci + 1}`} value={condition.input} style={{ width: 165 }}
                  options={definition.inputs.map((input) => ({ value: input.name, label: input.name }))}
                  onChange={(value) => updateCondition(index, ci, { input: value })} />
                <Select aria-label={`比较符 ${index + 1}-${ci + 1}`} value={condition.operator} style={{ width: 100 }}
                  options={operators.filter((op) => type === 'DECIMAL' || op === 'EQ' || op === 'NE')
                    .map((value) => ({ value, label: value }))}
                  onChange={(value: RuleCondition['operator']) => updateCondition(index, ci, { operator: value })} />
                {type === 'BOOLEAN' ? <Select aria-label={`预期值 ${index + 1}-${ci + 1}`}
                  value={String(condition.expected)} style={{ width: 110 }}
                  options={[{ value: 'true', label: 'true' }, { value: 'false', label: 'false' }]}
                  onChange={(value) => updateCondition(index, ci, { expected: value === 'true' })} />
                  : type === 'DECIMAL' ? <Input aria-label={`预期值 ${index + 1}-${ci + 1}`}
                    placeholder="精确十进制，如 100.05" value={String(condition.expected ?? '')}
                    onChange={(event) => updateCondition(index, ci, { expected: event.target.value })} />
                    : <Input aria-label={`预期值 ${index + 1}-${ci + 1}`} value={String(condition.expected ?? '')}
                      onChange={(e) => updateCondition(index, ci, { expected: e.target.value })} />}
                <Button danger onClick={() => updateRow(index, { conditions: row.conditions.filter((_, i) => i !== ci) })}>删除条件</Button>
              </Space>
            })}
            <Button size="small" onClick={() => updateRow(index, { conditions: [...row.conditions,
              { input: definition.inputs[0]?.name ?? '', operator: 'EQ', expected: '' }] })}>添加 AND 条件</Button>
            <Space wrap style={{ display: 'flex', marginTop: 12 }}>
              <Select aria-label={`结果码 ${index + 1}`} value={row.result.resultCode} style={{ width: 160 }}
                options={definition.resultCodes.map((value) => ({ value, label: value }))}
                onChange={(value) => updateRow(index, { result: editOutcome(row.result, { resultCode: value }) })} />
              <Input aria-label={`原因码 ${index + 1}`} placeholder="原因码" value={row.result.reasonCode}
                onChange={(e) => updateRow(index, { result: editOutcome(row.result, { reasonCode: e.target.value }) })} />
              <Input aria-label={`原因 ${index + 1}`} placeholder="原因" value={row.result.reason}
                onChange={(e) => updateRow(index, { result: editOutcome(row.result, { reason: e.target.value }) })} />
            </Space>
          </Card>)}
        </Card>
        <Card size="small" title="兜底结果" style={{ marginBottom: 12 }}><Space wrap>
          <Select aria-label="兜底结果码" value={definition.defaultResult.resultCode} style={{ width: 160 }}
            options={definition.resultCodes.map((value) => ({ value, label: value }))}
            onChange={(value) => change({ ...definition, defaultResult: editOutcome(definition.defaultResult, { resultCode: value }) })} />
          <Input aria-label="兜底原因码" value={definition.defaultResult.reasonCode}
            onChange={(e) => change({ ...definition, defaultResult: editOutcome(definition.defaultResult, { reasonCode: e.target.value }) })} />
          <Input aria-label="兜底原因" value={definition.defaultResult.reason}
            onChange={(e) => change({ ...definition, defaultResult: editOutcome(definition.defaultResult, { reason: e.target.value }) })} />
        </Space></Card>
        <Card size="small" title="模拟试算"><Typography.Paragraph type="secondary">
          DECIMAL 小数请用 JSON 字符串输入，如 {`{"amount":"0.10000000000000001"}`}，避免浏览器舍入。
        </Typography.Paragraph><Input.TextArea aria-label="试算输入 JSON" value={sample} rows={3}
          onChange={(e) => setSample(e.target.value)} />
          <Space wrap style={{ marginTop: 8 }}>
            <Button disabled={!existing || dirty || busy} onClick={() => void runTest()}>试算草稿</Button>
            <InputNumber aria-label="试算发布版本 ID" min={1} placeholder="发布版本 ID" value={testVersionId}
              onChange={(value) => setTestVersionId(value)} />
            <Button disabled={!existing || !testVersionId || busy}
              onClick={() => void runVersionTest()}>试算发布版本</Button>
          </Space>
          {result && <Typography.Paragraph style={{ marginTop: 8 }}>{result}</Typography.Paragraph>}
        </Card>
      </div>
      <Space style={{ marginTop: 16 }}>
        <Button type="primary" loading={busy} disabled={!dirty} onClick={() => void save()}>保存草稿</Button>
        <Button loading={busy} disabled={!existing || dirty} onClick={() => void publish()}>发布版本</Button>
        {dirty && <Typography.Text type="warning">当前有未保存修改</Typography.Text>}
      </Space>
    </Modal>
  </PageContainer>
}
