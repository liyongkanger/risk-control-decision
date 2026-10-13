/** 后端统一响应格式。 */
export type ApiResponse<T> = { code: string; message: string; data: T | null }

/** 读取业务响应，并保留后端错误说明。 */
export async function readApi<T>(response: Response): Promise<T> {
  const body = await response.json() as ApiResponse<T>
  if (!response.ok || body.code !== 'SUCCESS') {
    const data = body.data
    const issues = data && typeof data === 'object' && 'issues' in data ? data.issues : data
    const details = Array.isArray(issues)
      ? issues.map((issue) => `${issue.code}: ${issue.message}`).join('; ')
      : ''
    throw new Error(details || body.message || `HTTP ${response.status}`)
  }
  return body.data as T
}
