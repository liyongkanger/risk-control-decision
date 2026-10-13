import { history } from '@umijs/max'
import SceneListPage from '../scenes'

/** 流程设计菜单入口，先选择要设计的场景。 */
export default function FlowDesignIndex() {
  return <SceneListPage onDesign={(sceneCode) => history.push(`/scenes/${encodeURIComponent(sceneCode)}/flow`)} selectionMode />
}
