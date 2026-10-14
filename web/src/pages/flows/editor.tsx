import { Link, useParams } from '@umijs/max'
import { PageContainer } from '@ant-design/pro-components'
import FlowEditor from '../../flow/FlowEditor'

/** 独立流程资产的画布设计页。 */
export default function FlowAssetEditorPage() {
  const { flowCode } = useParams<{ flowCode: string }>()
  return <PageContainer title="流程设计" breadcrumb={{ items: [
    { title: <Link to="/flows">流程资产</Link> }, { title: '流程设计' },
  ] }}><FlowEditor target={{ kind: 'flow', code: flowCode ?? '' }} /></PageContainer>
}
