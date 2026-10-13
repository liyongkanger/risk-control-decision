import type { NodeKind } from './types'

/** 组件节点与条件节点分别使用对应类型的 LiteFlow 组件。 */
export function componentOptionsFor(kind: NodeKind): string[] {
  switch (kind) {
    case 'COMPONENT':
      return ['emit-value']
    case 'CONDITION':
      return ['is-positive']
    default:
      return []
  }
}
