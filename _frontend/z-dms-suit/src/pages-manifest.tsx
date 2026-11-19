import {
  ConsoleSqlOutlined,
  DatabaseOutlined,
  EyeOutlined,
  HistoryOutlined,
  HomeOutlined,
  ReadOutlined,
  SafetyOutlined,
} from '@ant-design/icons'
import HomePage from './pages/HomePage'
import SourcesPage from './pages/SourcesPage'
import BrowserPage from './pages/BrowserPage'
import SqlConsolePage from './pages/SqlConsolePage'
import ChangeHistoryPage from './pages/ChangeHistoryPage'
import AuditPage from './pages/AuditPage'
import ComponentDocPage from './pages/ComponentDoc'
import type { ComponentType, ReactNode } from 'react'

export interface MenuItem {
  key: string
  label: string
  icon: ReactNode
  /** 需要的权限点（权限管控开启时按 /auth/me 过滤菜单；undefined = 登录即可见）。 */
  permission?: string
}
export interface RouteEntry { path: string; Component: ComponentType }

/** 菜单 + 路由清单（显隐只是体验，真闸在服务端每个端点上）。 */
export const menuItems: MenuItem[] = [
  { key: '/z-dms/home', label: '首页', icon: <HomeOutlined /> },
  { key: '/z-dms/sources', label: '数据源', icon: <DatabaseOutlined />, permission: 'DMS_SOURCE_VIEW' },
  { key: '/z-dms/browser', label: '数据浏览', icon: <EyeOutlined />, permission: 'DMS_SCHEMA_VIEW' },
  { key: '/z-dms/console', label: 'SQL 控制台', icon: <ConsoleSqlOutlined />, permission: 'DMS_SQL_EXECUTE' },
  { key: '/z-dms/changes', label: '变更历史', icon: <HistoryOutlined />, permission: 'DMS_AUDIT_VIEW' },
  { key: '/z-dms/audit', label: '审计流水', icon: <SafetyOutlined />, permission: 'DMS_AUDIT_VIEW' },
  { key: '/z-dms/docs', label: '组件文档', icon: <ReadOutlined /> },
]

export const routes: RouteEntry[] = [
  { path: '/z-dms/home', Component: HomePage },
  { path: '/z-dms/sources', Component: SourcesPage },
  { path: '/z-dms/browser', Component: BrowserPage },
  { path: '/z-dms/console', Component: SqlConsolePage },
  { path: '/z-dms/changes', Component: ChangeHistoryPage },
  { path: '/z-dms/audit', Component: AuditPage },
  { path: '/z-dms/docs', Component: ComponentDocPage },
]

// 主壳接线用：文档页统一以 ComponentDocPage 这个名字从主入口导出。
export { default as ComponentDocPage } from './pages/ComponentDoc'
