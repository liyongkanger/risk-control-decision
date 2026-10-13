import { readApi } from './http'

/** 后台场景基本信息。 */
export type Scene = {
  sceneCode: string
  sceneName: string
  flowCode: string
  description: string | null
  publishedVersionNumber: number | null
  updatetime: string
}

/** 场景列表分页结果。 */
export type ScenePage = { records: Scene[]; total: number; page: number; pageSize: number }

/** 创建场景的可编辑字段。 */
export type CreateScene = { sceneCode: string; sceneName: string; description: string | null }

/** 编辑场景的可修改字段。 */
export type UpdateScene = { sceneName: string; description: string | null }

export async function listScenes(keyword = '', page = 1, pageSize = 20): Promise<ScenePage> {
  const query = new URLSearchParams({ keyword, page: String(page), pageSize: String(pageSize) })
  return readApi<ScenePage>(await fetch(`/api/scenes?${query}`))
}

export async function getScene(sceneCode: string): Promise<Scene> {
  return readApi<Scene>(await fetch(`/api/scenes/${encodeURIComponent(sceneCode)}`))
}

export async function createScene(request: CreateScene): Promise<Scene> {
  return readApi<Scene>(await fetch('/api/scenes', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }))
}

export async function updateScene(sceneCode: string, request: UpdateScene): Promise<Scene> {
  return readApi<Scene>(await fetch(`/api/scenes/${encodeURIComponent(sceneCode)}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }))
}

export async function bindSceneFlow(sceneCode: string, flowCode: string): Promise<Scene> {
  return readApi<Scene>(await fetch(`/api/scenes/${encodeURIComponent(sceneCode)}/entry-flow`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ flowCode }),
  }))
}
