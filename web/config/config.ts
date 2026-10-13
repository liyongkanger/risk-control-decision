import { defineConfig } from '@umijs/max'
import routes from './routes'

/** Ant Design Pro 后台的最小构建与路由配置。 */
export default defineConfig({
  title: '风控决策平台',
  routes,
  layout: { locale: false, title: '风控决策平台', layout: 'side' },
  antd: {},
  utoopack: {},
  proxy: { '/api': { target: process.env.RISK_API_TARGET ?? 'http://localhost:8080', changeOrigin: true } },
  npmClient: 'npm',
})
