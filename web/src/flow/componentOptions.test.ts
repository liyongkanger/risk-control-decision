import { describe, expect, it } from 'vitest'
import { componentOptionsFor } from './componentOptions'

describe('component options', () => {
  it('only offers executable components for a component node', () => {
    expect(componentOptionsFor('COMPONENT')).toEqual(['emit-value', 'get-variable', 'execute-rule-set'])
  })

  it('only offers condition components for a condition node', () => {
    expect(componentOptionsFor('CONDITION')).toEqual(['is-positive', 'equals-value', 'compare-value', 'rule-result-equals'])
  })
})
