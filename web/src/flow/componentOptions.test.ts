import { describe, expect, it } from 'vitest'
import { componentOptionsFor } from './componentOptions'

describe('component options', () => {
  it('only offers executable components for a component node', () => {
    expect(componentOptionsFor('COMPONENT')).toEqual(['emit-value'])
  })

  it('only offers condition components for a condition node', () => {
    expect(componentOptionsFor('CONDITION')).toEqual(['is-positive'])
  })
})
