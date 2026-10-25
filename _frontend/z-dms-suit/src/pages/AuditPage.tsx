import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Card, Input, Select, Space, Table, Tag } from 'antd'
import type { TableColumnsType } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { listAudit, listSources } from '../api'
import type { AuditEntry } from '../types'

/**
 * 审计流水页：<b>变更与使用都在这条流里</b>。
 *
 * <p>「使用」记录回答的是「谁在什么时候用了哪个数据源」——
 * 后端每次测连接 / 取连接都会写一条使用流水（谁、什么时候、结论、耗时）。
 */
export default function AuditPage() {
  const [rows, setRows] = useState<AuditEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [datasourceId, setDatasourceId] = useState<string | undefined>(undefined)
  const [category, setCategory] = useState<string | undefined>(undefined)
  const [sourceIds, setSourceIds] = useState<string[]>([])
  const [keyword, setKeyword] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await listAudit(datasourceId))
    } catch (e: any) {
      setRows([])
    } finally {
      setLoading(false)
    }
  }, [datasourceId])

  useEffect(() => {
    void reload()
  }, [reload])

  useEffect(() => {
    listSources()
      .then((list) => setSourceIds(list.map((s) => s.id)))
      .catch(() => setSourceIds([]))
  }, [])

  const data = category ? rows.filter((r) => r.category === category) : rows

  /** 客户端查询：按动作 / 数据源 / 操作人过滤（叠加在类别筛选之上）。 */
  const filteredData = useMemo(() => {
    const kw = keyword.trim().toLowerCase()
    if (!kw) return data
    return data.filter((r) =>
      `${r.action || ''} ${r.datasourceId || ''} ${r.operator || ''}`.toLowerCase().includes(kw),
    )
  }, [data, keyword])

  const columns: TableColumnsType<AuditEntry> = [
    {
      title: '时间',
      dataIndex: 'occurredAt',
      key: 'occurredAt',
      width: 180,
      render: (v: number) => (v ? new Date(v).toLocaleString('zh-CN') : '-'),
    },
    {
      title: '类别',
      dataIndex: 'category',
      key: 'category',
      width: 100,
      render: (v: string) => (
        <Tag color={v === 'CHANGE' ? 'blue' : 'purple'}>{v === 'CHANGE' ? '变更' : '使用'}</Tag>
      ),
    },
    { title: '动作', dataIndex: 'action', key: 'action', width: 170 },
    { title: '数据源', dataIndex: 'datasourceId', key: 'datasourceId', width: 150 },
    { title: '操作人', dataIndex: 'operator', key: 'operator', width: 130 },
    {
      title: '结果',
      dataIndex: 'success',
      key: 'success',
      width: 90,
      render: (v: boolean) => (v ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>),
    },
    {
      title: '耗时',
      dataIndex: 'latencyMs',
      key: 'latencyMs',
      width: 90,
      render: (v: number | null) => (v === null || v === undefined ? '-' : `${v} ms`),
    },
    { title: '详情', dataIndex: 'detail', key: 'detail', ellipsis: true },
  ]

  return (
    <div>
      <Card
        title="审计流水（谁改了什么 · 谁用了哪个数据源）"
        extra={
          <Space>
            <Input.Search
              allowClear
              placeholder="按动作 / 数据源 / 操作人过滤"
              style={{ width: 240 }}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <Select
              allowClear
              style={{ width: 200 }}
              placeholder="按类别过滤"
              value={category}
              onChange={setCategory}
              options={[
                { value: 'CHANGE', label: '变更' },
                { value: 'USAGE', label: '使用' },
              ]}
            />
            <Select
              allowClear
              style={{ width: 200 }}
              placeholder="按数据源过滤"
              value={datasourceId}
              onChange={setDatasourceId}
              options={sourceIds.map((id) => ({ value: id, label: id }))}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void reload()} loading={loading}>
              刷新
            </Button>
          </Space>
        }
      >
        <Table<AuditEntry>
          rowKey={(r) => `${r.category}-${r.occurredAt}-${r.datasourceId}-${r.action}`}
          size="small"
          columns={columns}
          dataSource={filteredData}
          loading={loading}
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条流水` }}
          scroll={{ x: 1100 }}
        />
      </Card>
    </div>
  )
}
