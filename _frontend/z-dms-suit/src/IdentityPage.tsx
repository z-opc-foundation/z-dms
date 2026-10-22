import { useState } from 'react'
import { App as AntdApp, Button, Card, Form, Input } from 'antd'
import { UserOutlined } from '@ant-design/icons'

/**
 * 身份选择页（代替口令登录页）。
 *
 * 【口径】形态 B 的界面**全链路没有口令输入框**：
 *   * 数据源凭据面只有 credentialRef 选择器（口令永远不进浏览器）；
 *   * 操作人身份在生产由平台 SSO / 网关以 X-Operator 头下发，
 *     本地形态下在这里选一个身份，好让审计流水能回答「谁改的、谁用的」。
 *
 * 所以这一页**没有密码框** —— 它不是登录页，是一个「以什么身份操作」的开关。
 */
export default function IdentityPage() {
  const [loading, setLoading] = useState(false)
  const { message } = AntdApp.useApp()

  const handleSubmit = (values: { operator: string }) => {
    setLoading(true)
    try {
      const name = values.operator.trim()
      if (!name) {
        message.error('请填写操作人')
        return
      }
      localStorage.setItem('dmsOperator', name)
      localStorage.setItem('dmsUser', JSON.stringify({ name, role: '数据源运维' }))
      message.success(`已以 ${name} 的身份进入`)
      window.location.href = '/z-dms/sources'
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      height: '100vh', display: 'flex', justifyContent: 'center', alignItems: 'center',
      background: 'linear-gradient(135deg, #eef2ff 0%, #f5f3ff 50%, #eff6ff 100%)',
    }}>
      <Card style={{ width: 420, boxShadow: '0 10px 40px rgba(124,58,237,0.12)' }}>
        <div style={{ textAlign: 'center', marginBottom: 20 }}>
          <div style={{
            width: 48, height: 48, borderRadius: 12, margin: '0 auto 12px',
            background: 'linear-gradient(135deg, #1677ff, #6d28d9)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#fff', fontSize: 24, fontWeight: 700,
          }}>z</div>
          <div style={{ fontSize: 18, fontWeight: 600, color: '#0f172a' }}>z-dms 数据源管控台</div>
          <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 4 }}>
            凭据面只有 credentialRef 选择器 · 界面无口令输入框
          </div>
        </div>
        <Form onFinish={handleSubmit} size="large">
          <Form.Item
            name="operator"
            label="操作人"
            rules={[{ required: true, message: '请填写操作人' }]}
            extra="生产环境该身份由平台 SSO / 网关以 X-Operator 头下发，这里只是本地形态的开关"
          >
            <Input prefix={<UserOutlined />} placeholder="例如：admin" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" loading={loading} block>
              进入控制台
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
