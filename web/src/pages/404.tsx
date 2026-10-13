import { Button, Result } from 'antd'
import { history } from '@umijs/max'

/** 后台未匹配路由的提示页。 */
export default function NotFoundPage() {
  return <Result status="404" title="页面不存在" extra={<Button type="primary" onClick={() => history.push('/scenes')}>返回场景管理</Button>} />
}
