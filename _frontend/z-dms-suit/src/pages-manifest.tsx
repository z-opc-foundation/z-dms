import { DatabaseOutlined, HistoryOutlined, ReadOutlined, SafetyOutlined } from '@ant-design/icons'
import SourcesPage from './pages/SourcesPage'
import ChangeHistoryPage from './pages/ChangeHistoryPage'
import AuditPage from './pages/AuditPage'
import ComponentDocPage from './pages/ComponentDoc'
import type { ComponentType, ReactNode } from 'react'

export interface MenuItem { key: string; label: string; icon: ReactNode }
export interface RouteEntry { path: string; Component: ComponentType }

/** 菜单 + 路由清单。 */
export const menuItems: MenuItem[] = [
  { key: '/z-dms/sources', label: '数据源', icon: <DatabaseOutlined /> },
  { key: '/z-dms/changes', label: '变更历史', icon: <HistoryOutlined /> },
  { key: '/z-dms/audit', label: '审计流水', icon: <SafetyOutlined /> },
  { key: '/z-dms/docs', label: '组件文档', icon: <ReadOutlined /> },
]

export const routes: RouteEntry[] = [
  { path: '/z-dms/sources', Component: SourcesPage },
  { path: '/z-dms/changes', Component: ChangeHistoryPage },
  { path: '/z-dms/audit', Component: AuditPage },
  { path: '/z-dms/docs', Component: ComponentDocPage },
]

// 主壳接线用：文档页统一以 ComponentDocPage 这个名字从主入口导出。
export { default as ComponentDocPage } from './pages/ComponentDoc'
