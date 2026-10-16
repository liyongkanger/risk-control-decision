import { PageContainer } from '@ant-design/pro-components'
import { useLocation } from '@umijs/max'
import { Button, Card, Input, Modal, Space, Table, Typography, message } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { createRuleSet, getRuleSet, getRuleSetVersion, listRuleSets, publishRuleSet, testRuleSet,
  testRuleSetVersion, updateRuleDraft } from '../../services/ruleCenter'
import type { RuleDefinition, RuleSet, RuleSetPage, RuleSetSummary } from '../../services/ruleCenter'
import { parseRuleTestInput } from './ruleInput'
import { isRuleInputReferenced, renameRuleInput, selectRuleVariable } from './variableSelection'
import VariablePicker from './VariablePicker'
import RuleVariableCard from './RuleVariableCard'
import UnmatchedResultEditor from './UnmatchedResultEditor'
import { getVariableVersion } from '../../services/variableCenter'
import type { VariableVersion } from '../../services/variableCenter'
import { buildRuleTrialSetup, retainRuleTestValues, setRuleTrialField } from './ruleTestSamples'
import type { RuleTrialField } from './ruleTestSamples'
import { createDraftRuleRow } from './ruleRowModel'
import RuleRowEditor from './RuleRowEditor'
import RuleTrialPanel from './RuleTrialPanel'
import type { TrialDisplayResult } from './RuleTrialPanel'
import './rule-center.css'

const emptyDefinition = (): RuleDefinition => ({
  inputs: [], resultCodes: ['PASS', 'REJECT'], rows: [],
  defaultResult: { resultCode: 'PASS', reasonCode: 'DEFAULT', reason: '符合规则' },
})

const parseSampleObject = (raw: string): Record<string, unknown> | null => {
  try {
    const value: unknown = JSON.parse(raw)
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, unknown> : null
  } catch { return null }
}

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
  const [sample, setSample] = useState('{}')
  const [contextSample, setContextSample] = useState('{}')
  const [samplesLoading, setSamplesLoading] = useState(false)
  const [trialFields, setTrialFields] = useState<RuleTrialField[]>([])
  const [trialTarget, setTrialTarget] = useState<'DRAFT' | 'PUBLISHED'>('DRAFT')
  const [publishedFieldsVersionId, setPublishedFieldsVersionId] = useState<number | null>(null)
  const [trialError, setTrialError] = useState<string | null>(null)
  const sampleRequest = useRef(0)
  const publishedRequest = useRef(0)
  const [testVersionId, setTestVersionId] = useState<number | null>(null)
  const [result, setResult] = useState<TrialDisplayResult | null>(null)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [pickerTarget, setPickerTarget] = useState<number | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try { setData(await listRuleSets(keyword, page, size)) }
    catch (error) { notice.error(error instanceof Error ? error.message : '规则集读取失败') }
    finally { setLoading(false) }
  }, [keyword, page, size, notice])
  useEffect(() => { void reload() }, [reload])
  const change = (next: RuleDefinition) => {
    setDefinition(next); setDirty(true); setResult(null); setTrialError(null)
  }
  const prepareSamples = async (next: RuleDefinition, preserveExisting = false): Promise<boolean> => {
    const request = ++sampleRequest.current
    const previousInput = preserveExisting ? parseSampleObject(sample) : null
    const previousContext = preserveExisting ? parseSampleObject(contextSample) : null
    setSamplesLoading(true)
    try {
      const setup = await buildRuleTrialSetup(next, getVariableVersion)
      if (request === sampleRequest.current) {
        const retained = retainRuleTestValues(setup.samples, {
          input: previousInput ?? {}, contextValues: previousContext ?? {},
        })
        setTrialFields(setup.fields)
        if (!preserveExisting || previousInput !== null) setSample(JSON.stringify(retained.input, null, 2))
        if (!preserveExisting || previousContext !== null) {
          setContextSample(JSON.stringify(retained.contextValues, null, 2))
        }
        if (preserveExisting && (previousInput === null || previousContext === null)) {
          notice.warning('试算样例 JSON 无效，已保留原文；请手动补充新变量')
        }
        return true
      }
    } catch (error) {
      if (request === sampleRequest.current) notice.error(error instanceof Error
        ? error.message : '试算样例准备失败')
    } finally { if (request === sampleRequest.current) setSamplesLoading(false) }
    return false
  }
  const preparePublishedSamples = async (id: number) => {
    const request = ++publishedRequest.current
    setPublishedFieldsVersionId(null)
    setTrialError(null)
    setSamplesLoading(true)
    try {
      const version = await getRuleSetVersion(id)
      if (request !== publishedRequest.current) return
      if (version.code !== code) throw new Error('该版本不属于当前规则集')
      if (await prepareSamples(version.definition, true) && request === publishedRequest.current) {
        setPublishedFieldsVersionId(id)
      }
    } catch (error) {
      if (request === publishedRequest.current) setTrialError(error instanceof Error
        ? error.message : '发布版本读取失败')
    } finally { if (request === publishedRequest.current) setSamplesLoading(false) }
  }
  const newRule = () => {
    sampleRequest.current++
    publishedRequest.current++
    setExisting(null); setCode(''); setName(''); setDefinition(emptyDefinition());
    setDirty(true); setResult(null); setSample('{}'); setContextSample('{}'); setSamplesLoading(false)
    setTrialFields([]); setTrialTarget('DRAFT'); setPublishedFieldsVersionId(null)
    setTrialError(null)
    setTestVersionId(null)
    setPickerOpen(false); setOpened(true)
  }
  const openRule = async (item: RuleSetSummary) => {
    const request = ++sampleRequest.current
    publishedRequest.current++
    try {
      const current = await getRuleSet(item.code)
      if (request !== sampleRequest.current) return
      setExisting(current); setCode(current.code); setName(current.name)
      setDefinition(current.definition); setDirty(false); setResult(null); setOpened(true)
      setTrialTarget('DRAFT'); setTrialError(null); setTrialFields([])
      setPublishedFieldsVersionId(null)
      setTestVersionId(current.currentVersionId)
      setSample('{}'); setContextSample('{}')
      void prepareSamples(current.definition)
    } catch (error) { notice.error(error instanceof Error ? error.message : '规则详情读取失败') }
  }
  const save = async () => {
    if (definition.inputs.length === 0) { notice.error('请先从变量中心选择至少一个变量'); return }
    if (definition.rows.some((row) => row.conditions.some((condition) => condition.expected === null))) {
      notice.error('请填写每个条件的比较值'); return
    }
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
      const contextValues = parseRuleTestInput(contextSample, { ...definition, inputs: [] })
      const value = await testRuleSet(code, input, contextValues)
      setResult({ evaluation: value, versionLabel: '已保存草稿' }); setTrialError(null)
    } catch (error) { setResult(null); setTrialError(error instanceof Error ? error.message : '规则试算失败') }
    finally { setBusy(false) }
  }
  const runVersionTest = async () => {
    if (!existing || !testVersionId) { notice.error('请选择已发布版本 ID'); return }
    setBusy(true)
    try {
      const version = await getRuleSetVersion(testVersionId)
      if (version.code !== code) throw new Error('该版本不属于当前规则集')
      const input = parseRuleTestInput(sample, version.definition)
      const contextValues = parseRuleTestInput(contextSample, { ...version.definition, inputs: [] })
      const value = await testRuleSetVersion(code, testVersionId, input, contextValues)
      setResult({ evaluation: value,
        versionLabel: `发布版 v${version.versionNumber} · ID ${version.id}` }); setTrialError(null)
    } catch (error) { setResult(null); setTrialError(error instanceof Error ? error.message : '发布版本试算失败') }
    finally { setBusy(false) }
  }
  const updateTrialField = (field: RuleTrialField, value: string | boolean) => {
    try {
      if (field.source === 'REQUEST_INPUT') setSample(setRuleTrialField(sample, field.code, value))
      else setContextSample(setRuleTrialField(contextSample, field.code, value))
      setResult(null); setTrialError(null)
    } catch (error) {
      setTrialError(error instanceof Error ? error.message : '试算数据格式无效')
    }
  }
  const chooseVariable = (variable: VariableVersion) => {
    try {
      const previousType = pickerTarget === null ? null : definition.inputs[pickerTarget]?.type
      const next = selectRuleVariable(definition, variable, pickerTarget ?? undefined)
      change(next)
      setPickerOpen(false)
      void prepareSamples(next, true)
      if (previousType && previousType !== variable.valueType) {
        notice.warning('变量类型已改变，相关条件已重置为“等于”；请重新填写比较值')
      }
    } catch (error) { notice.error(error instanceof Error ? error.message : '选择变量失败') }
  }
  const updateRow = (index: number, patch: Partial<RuleDefinition['rows'][number]>) => change({
    ...definition, rows: definition.rows.map((item, i) => i === index ? { ...item, ...patch } : item),
  })

  return <PageContainer title="规则中心">
    {holder}
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
        <Card size="small" title={`规则使用的变量（${definition.inputs.length}）`}
          style={{ marginBottom: 12 }} extra={<Button onClick={() => {
          setPickerTarget(null); setPickerOpen(true)
        }}>搜索添加变量</Button>}>
          <Typography.Paragraph type="secondary" className="rule-variables-hint">
            先从变量中心选择固定版本；规则输入名用于下方条件引用，可按业务含义调整。
          </Typography.Paragraph>
          {definition.inputs.length === 0 && <Typography.Text type="secondary">
            先从变量中心选择变量，再添加规则行和比较条件。
          </Typography.Text>}
          <div className="rule-variable-list">{definition.inputs.map((input, index) =>
            <RuleVariableCard key={`${input.variableBinding.variableCode}-${index}`}
              input={input} index={index}
              referenced={isRuleInputReferenced(definition, input.name)}
              onAliasChange={(name) => {
                try { change(renameRuleInput(definition, index, name)) }
                catch (error) { notice.error(error instanceof Error ? error.message : '规则输入名无效') }
              }}
              onReplace={() => { setPickerTarget(index); setPickerOpen(true) }}
              onRemove={() => {
                const next = { ...definition, inputs: definition.inputs.filter((_, i) => i !== index) }
                change(next); void prepareSamples(next, true)
              }} />)}</div>
        </Card>
        <Card size="small" title="业务结果码" style={{ marginBottom: 12 }}>
          <Input aria-label="结果码列表" value={definition.resultCodes.join(', ')}
            placeholder="PASS, REJECT" onChange={(e) => change({ ...definition,
              resultCodes: e.target.value.split(/[,，]/).map((item) => item.trim()).filter(Boolean) })} />
        </Card>
        <Card size="small" title="规则行" style={{ marginBottom: 12 }}
          extra={<Button type="primary" disabled={definition.inputs.length === 0}
            onClick={() => change({ ...definition,
              rows: [...definition.rows, createDraftRuleRow(definition)] })}>添加规则行</Button>}>
          <Typography.Paragraph type="secondary">按优先级从小到大依次判断；每行条件全部满足后返回结果，不再继续判断后续行。</Typography.Paragraph>
          {definition.rows.length === 0 && <div className="rule-row-empty">
            {definition.inputs.length === 0 ? '先在上方选择变量，再添加规则行。' : '还没有规则行，点击右上角添加。'}
          </div>}
          {definition.rows.map((row, index) => <RuleRowEditor key={index} row={row} index={index}
            inputs={definition.inputs} resultCodes={definition.resultCodes}
            onChange={(next) => updateRow(index, next)}
            onRemove={() => change({ ...definition,
              rows: definition.rows.filter((_, i) => i !== index) })} />)}
        </Card>
        <UnmatchedResultEditor value={definition.defaultResult} resultCodes={definition.resultCodes}
          ruleCount={definition.rows.length}
          onChange={(defaultResult) => change({ ...definition, defaultResult })} />
        <RuleTrialPanel fields={trialFields} requestText={sample} contextText={contextSample}
          onRequestTextChange={(text) => { setSample(text); setResult(null); setTrialError(null) }}
          onContextTextChange={(text) => { setContextSample(text); setResult(null); setTrialError(null) }}
          onFieldChange={updateTrialField} loading={samplesLoading} busy={busy}
          hasSavedDraft={!!existing} draftDirty={dirty}
          target={trialTarget} onTargetChange={(target) => {
            setTrialTarget(target); setResult(null); setTrialError(null)
            publishedRequest.current++
            if (target === 'PUBLISHED' && testVersionId) void preparePublishedSamples(testVersionId)
            if (target === 'DRAFT') void prepareSamples(definition, true)
          }}
          versionId={testVersionId} onVersionIdChange={(id) => {
            publishedRequest.current++; sampleRequest.current++
            setSamplesLoading(false); setPublishedFieldsVersionId(null)
            setTestVersionId(id); setResult(null); setTrialError(null)
          }}
          versionFieldsReady={publishedFieldsVersionId === testVersionId}
          onLoadPublishedFields={() => { if (testVersionId) void preparePublishedSamples(testVersionId) }}
          onRunDraft={() => void runTest()} onRunPublished={() => void runVersionTest()}
          result={result} error={trialError} />
      </div>
      <Space style={{ marginTop: 16 }}>
        <Button type="primary" loading={busy} disabled={!dirty} onClick={() => void save()}>保存草稿</Button>
        <Button loading={busy} disabled={!existing || dirty} onClick={() => void publish()}>发布版本</Button>
        {dirty && <Typography.Text type="warning">当前有未保存修改</Typography.Text>}
      </Space>
    </Modal>
    {pickerOpen && <VariablePicker open selectedVersionId={pickerTarget === null ? undefined
      : definition.inputs[pickerTarget]?.variableBinding.variableVersionId}
      onCancel={() => setPickerOpen(false)} onChoose={chooseVariable} />}
  </PageContainer>
}
