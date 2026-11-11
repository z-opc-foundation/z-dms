import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Input,
  Popconfirm,
  Space,
  Table,
  Tooltip,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { StatusTag } from '@yuku123/z-frontend-common'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { listSources, removeSource, testConnection } from '../api'
import type { SourceItem } from '../types'
import SourceFormDrawer from './SourceFormDrawer'

/** 健康态 → 语义色（基座 StatusTag 的 map 覆盖）。 */
const HEALTH_MAP: Record<string, { color?: string; text: string }> = {
  UP: { color: 'green', text: 'UP' },
  DOWN: { color: 'red', text: 'DOWN' },
  UNKNOWN: { color: 'default', text: '未探活' },
}

/**
 * 数据源列表页：<b>一屏看到全部登记数据源与各自健康态</b>。
 *
 * <p>健康态是后端<b>当场探出来</b>的（逐池真借一次连接跑 SELECT 1），
 * 「测连接」按钮也是真发起连接 —— 不是回固定值，结论带真实耗时与根因。
 */
export default function SourcesPage() {
  const { message } = AntdApp.useApp()
  const [items, setItems] = useState<SourceItem[]>([])
  const [loading, setLoading] = useState(false)
  const [testingId, setTestingId] = useState<string | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<SourceItem | null>(null)
  const [keyword, setKeyword] = useState('')

  /** 客户端查询：按数据源 ID / 名称 / 类型过滤（不改动接口返回的原始集合）。 */
  const filteredItems = useMemo(() => {
    const kw = keyword.trim().toLowerCase()
    if (!kw) return items
    return items.filter((item) =>
      `${item.id || ''} ${item.name || ''} ${item.type || ''}`.toLowerCase().includes(kw),
    )
  }, [items, keyword])

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setItems(await listSources())
    } catch (e: any) {
      message.error(e?.message || '加载数据源列表失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    void reload()
  }, [reload])

  const handleTest = async (row: SourceItem) => {
    setTestingId(row.id)
    try {
      const probe = await testConnection(row.id)
      if (probe.up) {
        message.success(`${row.id} 连通（${probe.latencyMs ?? '-'} ms，目标 ${probe.target ?? '-'}）`)
      } else {
        message.error(`${row.id} 连不通：${probe.detail ?? '未知原因'}`)
      }
      await reload()
    } catch (e: any) {
      message.error(e?.message || '测连接失败')
    } finally {
      setTestingId(null)
    }
  }

  const handleDelete = async (row: SourceItem) => {
    try {
      await removeSource(row.id)
      message.success(`已摘除 ${row.id}`)
      await reload()
    } catch (e: any) {
      message.error(e?.message || '摘除失败')
    }
  }

  const columns: TableColumnsType<SourceItem> = [
    { title: '数据源 ID', dataIndex: 'id', key: 'id', width: 160 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 160 },
    { title: '类型', dataIndex: 'type', key: 'type', width: 90 },
    {
      title: '凭据引用（credentialRef）',
      dataIndex: 'credentialRef',
      key: 'credentialRef',
      width: 220,
      render: (v: string | null) => (v ? v : <span style={{ color: '#94a3b8' }}>（无）</span>),
    },
    {
      title: '健康态',
      dataIndex: 'health',
      key: 'health',
      width: 100,
      render: (v: string) => <StatusTag status={v} map={HEALTH_MAP} />,
    },
    {
      title: '延迟',
      dataIndex: 'latencyMs',
      key: 'latencyMs',
      width: 90,
      render: (v: number | null) => (v === null || v === undefined ? '-' : `${v} ms`),
    },
    {
      title: '探活结论',
      dataIndex: 'detail',
      key: 'detail',
      ellipsis: true,
      render: (v: string | null) => (
        <Tooltip title={v || ''}>
          <span style={{ color: v ? '#0f172a' : '#94a3b8' }}>{v || '-'}</span>
        </Tooltip>
      ),
    },
    {
      title: '操作',
      key: 'ops',
      width: 230,
      render: (_: unknown, row: SourceItem) => (
        <Space size={4}>
          <Button
            size="small"
            type="primary"
            loading={testingId === row.id}
            onClick={() => void handleTest(row)}
          >
            测连接
          </Button>
          <Button type="link"
            size="small"
            onClick={() => { setEditing(row); setDrawerOpen(true) }}
          >
            编辑
          </Button>
          <Popconfirm
            title={`摘除 ${row.id}？`}
            description="会显式关闭连接池，并在变更历史里留一条 REMOVED。"
            onConfirm={() => void handleDelete(row)}
          >
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Card
        title="数据源（组织级登记台账）"
        extra={
          <Space>
            <Input.Search
              allowClear
              placeholder="按数据源 ID / 名称 / 类型过滤"
              style={{ width: 240 }}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void reload()} loading={loading}>
              刷新并重新探活
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => { setEditing(null); setDrawerOpen(true) }}
            >
              登记数据源
            </Button>
          </Space>
        }
      >
        <Table<SourceItem>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={filteredItems}
          loading={loading}
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 个数据源` }}
          scroll={{ x: 1100 }}
        />
      </Card>

      <SourceFormDrawer
        open={drawerOpen}
        editing={editing}
        onClose={() => { setDrawerOpen(false); setEditing(null) }}
        onSaved={() => { setDrawerOpen(false); setEditing(null); void reload() }}
      />
    </div>
  )
}
