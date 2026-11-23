import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout, ErrorBoundary } from '@yuku123/z-frontend-common'
import { Button, Result, Spin } from 'antd'
import { menuItems, routes } from './pages-manifest'
import IdentityPage from './IdentityPage'
import { fetchMe } from './api'
import type { MeInfo } from './types'

function NotFound() {
  return (
    <Result
      status="404"
      title="页面不存在"
      extra={<Button type="primary" onClick={() => { window.location.href = menuItems[0].key }}>去首页</Button>}
    />
  )
}

function NeedLogin({ loginUrl }: { loginUrl?: string | null }) {
  return (
    <Result
      status="403"
      title="尚未登录"
      subTitle="请先通过统一登录后再使用控制台。"
      extra={loginUrl ? (
        <Button type="primary" onClick={() => { window.location.href = loginUrl }}>去登录</Button>
      ) : (
        <Button onClick={() => window.location.reload()}>刷新重试</Button>
      )}
    />
  )
}

function ProtectedShell({ me }: { me: MeInfo }) {
  const name = me.enabled ? me.display || '当前用户' : localStorage.getItem('dmsOperator')
  if (!name) return <Navigate to="/z-dms/identity" replace />
  const permissions = new Set(me.permissions)
  const visibleMenu = !me.enabled || permissions.has('DMS_GLOBAL_ADMIN')
    ? menuItems
    : menuItems.filter(item => !item.permission || permissions.has(item.permission))

  return (
    <AppLayout
      menuItems={visibleMenu}
      appTitle="z-dms 数据源管控台"
      appShort="z-dms"
      appVersion="1.0.0"
      appUser={{ name, role: me.enabled ? 'z-ctc 登录' : '数据源运维' }}
      appIcon={{
        icon: <img src="/icon.png" alt="z-dms 数据源管控台" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 8 }} />,
        color: '#4f46e5',
        label: 'z-dms 数据源管控台',
      }}
      tokenKey={me.enabled ? 'token' : 'dmsOperator'}
      userInfoKey={me.enabled ? 'userInfo' : 'dmsUser'}
      loginPath="/z-dms/identity"
    />
  )
}

function AuthenticatedRoutes() {
  const [me, setMe] = useState<MeInfo | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    fetchMe()
      .then(value => { if (active) setMe(value) })
      .catch((failure: unknown) => {
        if (active) setError(failure instanceof Error ? failure.message : '暂时无法获取权限')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [attempt])

  const retry = () => {
    setLoading(true)
    setError(null)
    setMe(null)
    setAttempt(value => value + 1)
  }

  if (loading) {
    return <div role="status" aria-label="正在确认访问权限" style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}><Spin size="large" /></div>
  }
  if (error || !me || me.degraded) {
    return <Result
      status="warning"
      title="暂时无法确认访问权限"
      subTitle={error || '权限服务暂时不可用，请稍后重试。'}
      extra={<Button type="primary" onClick={retry}>重新加载</Button>}
    />
  }
  if (me.enabled && !me.authenticated) return <NeedLogin loginUrl={me.loginUrl} />

  return (
    <Routes>
      <Route path="/z-dms/identity" element={me.enabled ? <Navigate to="/" replace /> : <IdentityPage />} />
      <Route element={<ProtectedShell me={me} />}>
        <Route path="/" element={<Navigate to={menuItems[0].key} replace />} />
        {routes.map(route => <Route key={route.path} path={route.path} element={<route.Component />} />)}
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  return <ErrorBoundary><BrowserRouter><AuthenticatedRoutes /></BrowserRouter></ErrorBoundary>
}
