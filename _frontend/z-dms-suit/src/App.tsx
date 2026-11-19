import { useEffect, useMemo, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout, ErrorBoundary } from '@yuku123/z-frontend-common'
import { Button, Result, Spin } from 'antd'
import { menuItems, routes } from './pages-manifest'
import IdentityPage from './IdentityPage'
import { fetchMe } from './api'
import type { MeInfo } from './types'

interface Operator {
  name?: string
  role?: string
}

function NotFound() {
  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="路由表里没有这条路径。"
      extra={
        <Button type="primary" onClick={() => { window.location.href = menuItems[0].key }}>
          去首页
        </Button>
      }
    />
  )
}

/** 未登录态：给出去 z-ctc 登录的入口（权限管控开启且后端 401 时）。 */
function NeedLogin({ loginUrl }: { loginUrl?: string | null }) {
  return (
    <Result
      status="403"
      title="尚未登录"
      subTitle="z-dms 权限管控已开启，需要先通过 z-ctc（4A 中心）登录后再使用管控台。"
      extra={
        loginUrl ? (
          <Button type="primary" onClick={() => { window.location.href = loginUrl }}>
            去 z-ctc 登录
          </Button>
        ) : (
          <Button onClick={() => window.location.reload()}>刷新重试</Button>
        )
      }
    />
  )
}

/**
 * 受保护壳：权限感知的菜单。
 *
 * <p>启动时拉 /auth/me：
 * <ul>
 *   <li>权限管控关闭（enabled=false）→ 免登录模式，维持 X-Operator 身份选择口径；</li>
 *   <li>开启且已登录 → 菜单按权限点显隐（真闸在服务端，这里只是体验）；</li>
 *   <li>开启但未登录 → 登录引导页（不猜身份、不放行）。</li>
 * </ul>
 */
function ProtectedShell() {
  const [operator, setOperator] = useState<Operator | null>(null)
  const [me, setMe] = useState<MeInfo | null>(null)
  const [meReady, setMeReady] = useState(false)

  useEffect(() => {
    fetchMe()
      .then(setMe)
      .catch(() => setMe({ enabled: false, authenticated: false, degraded: false, permissions: [] }))
      .finally(() => setMeReady(true))
  }, [])

  useEffect(() => {
    if (me && !me.enabled) {
      // 免登录模式（历史口径）：没有操作人身份就去身份选择页
      const name = localStorage.getItem('dmsOperator')
      if (name) {
        setOperator({ name, role: '数据源运维' })
      } else {
        window.location.replace('/z-dms/identity')
      }
    } else if (me?.authenticated) {
      setOperator({ name: me.display ?? undefined, role: 'z-ctc 登录' })
    }
  }, [me])

  const visibleMenu = useMemo(() => {
    if (!me || !me.enabled) return menuItems
    const perms = new Set(me.permissions)
    if (perms.has('DMS_GLOBAL_ADMIN')) return menuItems
    return menuItems.filter((m) => !m.permission || perms.has(m.permission))
  }, [me])

  if (!meReady) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}>
        <Spin size="large" />
      </div>
    )
  }

  if (me?.enabled && !me.authenticated) {
    return <NeedLogin loginUrl={me.loginUrl} />
  }

  if (!operator) return null

  return (
    <AppLayout
      menuItems={visibleMenu}
      appTitle="z-dms 数据源管控台"
      appShort="z-dms"
      appVersion="1.0.0"
      appUser={operator}
      appIcon={{
        icon: <img src="/icon.png" alt="z-dms 数据源管控台" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 8 }} />,
        color: '#4f46e5',
        label: 'z-dms 数据源管控台',
      }}
      loginPath="/z-dms/identity"
    />
  )
}

function HomeRedirect() {
  return <Navigate to={menuItems[0].key} replace />
}

export default function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <Routes>
          <Route path="/z-dms/identity" element={<IdentityPage />} />
          <Route element={<ProtectedShell />}>
            <Route path="/" element={<HomeRedirect />} />
            {routes.map((r) => (
              <Route key={r.path} path={r.path} element={<r.Component />} />
            ))}
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ErrorBoundary>
  )
}
