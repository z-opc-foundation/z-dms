import { DatabaseOutlined, HistoryOutlined, SafetyOutlined } from '@ant-design/icons'
import SourcesPage from './pages/SourcesPage'
import ChangeHistoryPage from './pages/ChangeHistoryPage'
import AuditPage from './pages/AuditPage'
import type { ComponentType, ReactNode } from 'react'

export interface MenuItem { key: string; label: string; icon: ReactNode }
export interface RouteEntry { path: string; Component: ComponentType }

/** 菜单 + 路由清单。 */
export const menuItems: MenuItem[] = [
  { key: '/z-dms/sources', label: '数据源', icon: <DatabaseOutlined /> },
  { key: '/z-dms/changes', label: '变更历史', icon: <HistoryOutlined /> },
  { key: '/z-dms/audit', label: '审计流水', icon: <SafetyOutlined /> },
]

export const routes: RouteEntry[] = [
  { path: '/z-dms/sources', Component: SourcesPage },
  { path: '/z-dms/changes', Component: ChangeHistoryPage },
  { path: '/z-dms/audit', Component: AuditPage },
]
