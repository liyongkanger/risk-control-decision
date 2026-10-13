/** 后台首版静态菜单与业务路由。 */
export default [
  { path: '/', redirect: '/scenes' },
  { path: '/scenes', name: '场景管理', icon: 'appstore', component: './scenes' },
  { path: '/flows', name: '流程资产', icon: 'apartment', component: './flows' },
  { path: '/flows/:flowCode/editor', name: '流程设计', hideInMenu: true, component: './flows/editor' },
  { path: '/flow-design', name: '流程设计', icon: 'partition', component: './flow-design' },
  { path: '/scenes/:sceneCode/flow', name: '流程设计', hideInMenu: true, component: './flow-design/editor' },
  { path: '/*', component: './404', layout: false },
]
