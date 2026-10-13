import type { RunTimeLayoutConfig } from '@umijs/max'

/** 首版使用静态菜单与展示用模拟用户。 */
export const layout: RunTimeLayoutConfig = () => ({
  collapsed: false,
  breakpoint: false,
  siderWidth: 208,
  avatarProps: { title: '演示用户' },
})
