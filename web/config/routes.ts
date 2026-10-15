/** 后台首版静态菜单与业务路由。 */
export default [
  { path: '/', redirect: '/scenes' },
  { path: '/scenes', name: '场景管理', icon: 'appstore', component: './scenes' },
  { path: '/flows', name: '流程资产', icon: 'apartment', component: './flows' },
  { path: '/components', name: '组件管理', icon: 'api', routes: [
    { path: '/components', redirect: '/components/data' },
    { path: '/components/data', name: '取值组件', component: './components' },
    { path: '/components/condition', name: '判断组件', component: './components' },
    { path: '/components/rule', name: '规则组件', component: './components' },
  ] },
  { path: '/variable-center', name: '变量中心', icon: 'database', component: './variable-center' },
  { path: '/rule-center', name: '规则中心', icon: 'deploymentUnit', component: './rule-center' },
  { path: '/marketing-demo', name: '营销闭环演示', icon: 'experiment', component: './marketing-demo' },
  { path: '/flows/:flowCode/editor', name: '流程设计', hideInMenu: true, component: './flows/editor' },
  { path: '/flow-design', name: '流程设计', icon: 'partition', component: './flow-design' },
  { path: '/scenes/:sceneCode/flow', name: '流程设计', hideInMenu: true, component: './flow-design/editor' },
  { path: '/*', component: './404', layout: false },
]
