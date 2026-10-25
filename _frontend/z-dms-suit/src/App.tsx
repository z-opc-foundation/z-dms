import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout, ErrorBoundary } from '@yuku123/z-frontend-common'
import { Button, Result } from 'antd'
import { menuItems, routes } from './pages-manifest'
import IdentityPage from './IdentityPage'

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

/**
 * 受保护壳：没有操作人身份就去身份选择页。
 *
 * 【为什么没有口令登录页】
 * 形态 B 的凭据纪律是「界面全链路无明文口令输入框」——
 * 数据源凭据面只有 credentialRef 选择器，而本控制台的操作人身份由
 * 平台 SSO / 网关以 X-Operator 头下发（审计流水要靠它回答「谁」）。
 * 本地形态下用「身份选择」页代替口令登录：**这里也没有任何口令输入框**。
 */
function ProtectedShell() {
  const [operator, setOperator] = useState<Operator | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    const name = localStorage.getItem('dmsOperator')
    if (!name) {
      window.location.replace('/z-dms/identity')
      return
    }
    setOperator({ name, role: '数据源运维' })
    setReady(true)
  }, [])

  if (!ready) return null

  return (
    <AppLayout
      menuItems={menuItems}
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

export default function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <Routes>
          <Route path="/z-dms/identity" element={<IdentityPage />} />
          <Route element={<ProtectedShell />}>
            <Route path="/" element={<Navigate to={menuItems[0].key} replace />} />
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
