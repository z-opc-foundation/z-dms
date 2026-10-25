import { useEffect, useState } from 'react'
import { Card, Col, Row, Space, Statistic, Tag, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { listSources } from '../api'
import type { SourceItem } from '../types'
import { menuItems } from '../pages-manifest'

const { Title, Text } = Typography

/** 首页（lead 008 §16）：欢迎语 + 真实统计 + 快捷入口。 */
export default function HomePage() {
  const navigate = useNavigate()
  const [sources, setSources] = useState<SourceItem[] | null>(null)
  const [failed, setFailed] = useState(false)
  const operator = localStorage.getItem('dmsOperator') || ''

  useEffect(() => {
    let cancelled = false
    listSources()
      .then((rows) => { if (!cancelled) setSources(rows) })
      .catch(() => { if (!cancelled) setFailed(true) })
    return () => { cancelled = true }
  }, [])

  const cards = menuItems.filter((m) => m.key !== '/z-dms/home')
  const statLoading = sources === null && !failed
  const stat = (value: number | undefined) => (statLoading ? '…' : value ?? '-')

  return (
    <div>
      <Card
        style={{ marginBottom: 16, background: 'linear-gradient(135deg, #4f46e5 0%, #0ea5e9 100%)', border: 'none' }}
      >
        <Space direction="vertical" size={4}>
          <Title level={3} style={{ color: '#fff', margin: 0 }}>
            欢迎{operator ? `，${operator}` : ''}
          </Title>
          <Text style={{ color: 'rgba(255,255,255,0.85)' }}>z-dms 数据源管控台</Text>
          <Space size={8} style={{ marginTop: 8 }}>
            <Tag style={{ background: 'rgba(255,255,255,0.2)', color: '#fff', border: 'none' }}>v1.0.0</Tag>
            <Tag style={{ background: 'rgba(255,255,255,0.2)', color: '#fff', border: 'none' }}>操作人：{operator || '未选择'}</Tag>
          </Space>
        </Space>
      </Card>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={8}>
          <Card><Statistic title="已登记数据源" value={stat(sources?.length)} /></Card>
        </Col>
        <Col xs={12} sm={8}>
          <Card><Statistic title="健康（UP）" value={stat(sources?.filter((s) => s.health === 'UP').length)} /></Card>
        </Col>
        <Col xs={12} sm={8}>
          <Card><Statistic title="异常 / 未知" value={stat(sources?.filter((s) => s.health !== 'UP').length)} /></Card>
        </Col>
      </Row>

      {failed && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          统计数据读取失败，可到「数据源」页重试。
        </Text>
      )}

      <Row gutter={[16, 16]}>
        {cards.map((m) => (
          <Col key={m.key} xs={24} sm={12} md={12} lg={8}>
            <Card hoverable onClick={() => navigate(m.key)} style={{ borderTop: '3px solid #4f46e5' }}>
              <Space align="start" size={12}>
                <div style={{
                  width: 44, height: 44, borderRadius: 8, flexShrink: 0,
                  background: 'rgba(79,70,229,0.08)', color: '#4f46e5',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20,
                }}>
                  {m.icon}
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 15, fontWeight: 600 }}>{m.label}</div>
                  <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>{m.key}</div>
                </div>
              </Space>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  )
}
