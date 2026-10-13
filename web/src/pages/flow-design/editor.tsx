import { Link, useParams } from '@umijs/max'
import { PageContainer } from '@ant-design/pro-components'
import FlowEditor from '../../flow/FlowEditor'

/** 当前场景的流程设计页。 */
export default function FlowDesignPage() {
  const { sceneCode } = useParams<{ sceneCode: string }>()
  return <PageContainer title="流程设计" breadcrumb={{ items: [
    { title: <Link to="/scenes">场景管理</Link> }, { title: '流程设计' },
  ] }}><FlowEditor target={{ kind: 'scene', code: sceneCode ?? '' }} /></PageContainer>
}
