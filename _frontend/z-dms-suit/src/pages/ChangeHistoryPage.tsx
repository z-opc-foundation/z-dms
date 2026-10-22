import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Card, Select, Space, Table, Tag } from 'antd'
import type { TableColumnsType } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { listChanges, listSources } from '../api'
import type { ChangeRecord, DefSnapshot } from '../types'

const CHANGE_TAG: Record<string, { color?: string; text: string }> = {
  REGISTERED: { color: 'green', text: '登记' },
  UPDATED: { color: 'blue', text: '修改' },
  REMOVED: { color: 'red', text: '摘除' },
}

/** 快照 JSON → 扁平字段列表（properties 展开成 properties.xxx）。 */
function flatten(snapshot?: string | null): Array<{ field: string; value: string }> {
  if (!snapshot) return []
  let obj: DefSnapshot
  try {
    obj = JSON.parse(snapshot) as DefSnapshot
  } catch {
    return [{ field: '(原文)', value: snapshot }]
  }
  const out = [
    { field: 'id', value: obj.id ?? '' },
    { field: 'name', value: obj.name ?? '' },
    { field: 'type', value: obj.type ?? '' },
    { field: 'credentialRef', value: obj.credentialRef ?? '' },
  ]
  const props = obj.properties || {}
  Object.keys(props).forEach((k) => {
    out.push({ field: `properties.${k}`, value: props[k] ?? '' })
  })
  return out
}

/**
 * 变更历史页：<b>每一次修改的前后值</b>。
 *
 * <p>展开一行就是「改了什么」的逐字段对照：左边变更前、右边变更后，
 * 不同的字段标红。后端存的是定义快照 JSON（含 credentialRef，**不含任何口令**）。
 */
export default function ChangeHistoryPage() {
  const [rows, setRows] = useState<ChangeRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [datasourceId, setDatasourceId] = useState<string | undefined>(undefined)
  const [sourceIds, setSourceIds] = useState<string[]>([])

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await listChanges(datasourceId))
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

  const columns: TableColumnsType<ChangeRecord> = [
    {
      title: '时间',
      dataIndex: 'changedAt',
      key: 'changedAt',
      width: 180,
      render: (v: number) => (v ? new Date(v).toLocaleString('zh-CN') : '-'),
    },
    { title: '数据源', dataIndex: 'datasourceId', key: 'datasourceId', width: 160 },
    {
      title: '变更类型',
      dataIndex: 'changeType',
      key: 'changeType',
      width: 100,
      render: (v: string) => <Tag color={CHANGE_TAG[v]?.color}>{CHANGE_TAG[v]?.text || v}</Tag>,
    },
    { title: '操作人', dataIndex: 'operator', key: 'operator', width: 140 },
    { title: '摘要', dataIndex: 'detail', key: 'detail', ellipsis: true },
  ]

  /** 前后值对照（字段名对齐，值不同的高亮）。 */
  const diff = useMemo(() => {
    return (row: ChangeRecord) => {
      const before = flatten(row.beforeValue)
      const after = flatten(row.afterValue)
      const fields = Array.from(new Set([...before.map((b) => b.field), ...after.map((a) => a.field)]))
      const beforeMap = new Map(before.map((b) => [b.field, b.value]))
      const afterMap = new Map(after.map((a) => [a.field, a.value]))
      return fields.map((f) => {
        const b = beforeMap.get(f) ?? ''
        const a = afterMap.get(f) ?? ''
        return { field: f, before: b, after: a, changed: b !== a }
      })
    }
  }, [])

  return (
    <div>
      <Card
        title="变更历史（每一次修改的前后值）"
        extra={
          <Space>
            <Select
              allowClear
              style={{ width: 220 }}
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
        <Table<ChangeRecord>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条变更` }}
          expandable={{
            expandedRowRender: (row) => {
              const fields = diff(row)
              if (!fields.length) {
                return <div style={{ color: '#94a3b8' }}>这条流水没有快照（历史数据或摘要型记录）。</div>
              }
              return (
                <div>
                  <div style={{ display: 'flex', fontWeight: 600, marginBottom: 8 }}>
                    <div style={{ width: 220 }}>字段</div>
                    <div style={{ width: 320 }}>变更前</div>
                    <div style={{ width: 320 }}>变更后</div>
                  </div>
                  {fields.map((f) => (
                    <div key={f.field} style={{ display: 'flex', marginBottom: 4 }}>
                      <div style={{ width: 220, color: '#64748b' }}>{f.field}</div>
                      <div style={{ width: 320, color: f.changed ? '#b91c1c' : '#334155' }}>
                        {f.before === '' ? '—' : f.before}
                      </div>
                      <div style={{ width: 320, color: f.changed ? '#b91c1c' : '#334155' }}>
                        {f.after === '' ? '—' : f.after}
                      </div>
                    </div>
                  ))}
                </div>
              )
            },
          }}
        />
      </Card>
    </div>
  )
}
