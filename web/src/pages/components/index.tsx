import { PageContainer } from '@ant-design/pro-components'
import { Button, Descriptions, Drawer, Input, Space, Table, Tag, Typography, message } from 'antd'
import { useLocation } from '@umijs/max'
import { useCallback, useEffect, useState } from 'react'
import { searchComponentDefinitions } from '../../services/componentCatalog'
import type { ComponentCatalogPage, ComponentDefinition } from '../../services/componentCatalog'

const outputLabels: Record<ComponentDefinition['outputType'], string> = {
  ANY: '配置值', BOOLEAN: '布尔分支', VARIABLE_VALUE: '变量版本决定的强类型单值',
  RULE_RESULT: '规则结果、原因及命中行',
}

/** 只读展示平台已接入的组件能力及配置契约。 */
export default function ComponentCatalogPageView() {
  const location = useLocation()
  const category = location.pathname.endsWith('/rule') ? 'RULE'
    : location.pathname.endsWith('/condition') ? 'CONDITION' : 'DATA'
  const title = category === 'RULE' ? '规则组件' : category === 'CONDITION' ? '判断组件' : '取值组件'
  const [notice, holder] = message.useMessage()
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ComponentCatalogPage>({ records: [], total: 0, page: 1, pageSize: 10 })
  const [selected, setSelected] = useState<ComponentDefinition | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try { setResult(await searchComponentDefinitions(keyword, '', page, pageSize, category)) }
    catch (error) { notice.error(error instanceof Error ? error.message : '组件目录加载失败') }
    finally { setLoading(false) }
  }, [keyword, category, page, pageSize, notice])
  useEffect(() => { void reload() }, [reload])

  return <PageContainer title={title}>
    {holder}
    <Typography.Paragraph type="secondary">
      这里查看已接入的{title}能力。变量定义在“变量中心”维护，规则集在“规则中心”维护；开始、结束和汇合节点在流程画布中使用。
    </Typography.Paragraph>
    <Space wrap style={{ marginBottom: 16 }}>
      <Input.Search aria-label="搜索组件" value={keywordInput} placeholder="按组件标识或名称搜索"
        allowClear onChange={(event) => setKeywordInput(event.target.value)}
        onSearch={() => { setPage(1); setKeyword(keywordInput.trim()) }} style={{ width: 290 }} />
    </Space>
    <Table<ComponentDefinition> rowKey="key" loading={loading} dataSource={result.records}
      pagination={{ current: page, pageSize, total: result.total, showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100], showTotal: (total) => `共 ${total} 项`,
        onChange: (nextPage, nextSize) => { setPage(nextSize === pageSize ? nextPage : 1); setPageSize(nextSize) } }}
      columns={[
        { title: '组件能力', dataIndex: 'displayName', width: 160 },
        { title: '标识', dataIndex: 'key', width: 150 },
        { title: '适用节点', dataIndex: 'condition', width: 120,
          render: (condition: boolean) => <Tag color={condition ? 'orange' : 'green'}>{condition ? '条件判断' : '普通组件'}</Tag> },
        { title: '用途', dataIndex: 'description', ellipsis: true },
        { title: '输出', dataIndex: 'outputType', width: 180,
          render: (value: ComponentDefinition['outputType']) => outputLabels[value] ?? value },
        { title: '详情', width: 100,
          render: (_: unknown, item: ComponentDefinition) => <Button type="link" onClick={() => setSelected(item)}>查看契约</Button> },
      ]} />
    <Drawer title={selected?.displayName} open={selected !== null} size={560}
      onClose={() => setSelected(null)}>
      {selected && <>
        <Typography.Paragraph>{selected.description}</Typography.Paragraph>
        <Descriptions column={1} bordered size="small" items={[
          { key: 'key', label: '组件标识', children: selected.key },
          { key: 'kind', label: '适用节点', children: selected.condition ? '条件判断' : '普通组件' },
          { key: 'output', label: '输出契约', children: outputLabels[selected.outputType] ?? selected.outputType },
        ]} />
        <Typography.Title level={5} style={{ marginTop: 24 }}>配置字段</Typography.Title>
        <Table rowKey="name" size="small" pagination={false} dataSource={selected.configFields}
          columns={[
            { title: '字段', dataIndex: 'name' }, { title: '含义', dataIndex: 'label' },
            { title: '类型', dataIndex: 'type' },
            { title: '必填', dataIndex: 'required', render: (value: boolean) => value ? '是' : '否' },
          ]} />
        <Typography.Title level={5} style={{ marginTop: 24 }}>初始配置</Typography.Title>
        <Typography.Paragraph type="secondary">创建节点时的初始值仍需通过流程校验。</Typography.Paragraph>
        <pre style={{ overflow: 'auto', padding: 12, background: '#f6f8fa', borderRadius: 6 }}>
          {JSON.stringify(selected.defaultConfig, null, 2)}
        </pre>
      </>}
    </Drawer>
  </PageContainer>
}
