import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Input,
  Pagination,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { listExplorerSources, listSchemas, listTables, tableDetail, tableRows } from '../api'
import type { ColumnMeta, ExplorerSource, IndexMeta, SchemaNode, TableDetail, TableQuery, TableFilter } from '../types'

const PAGE_SIZES = [20, 50, 100, 200]

const FILTER_OPS: { value: TableFilter['op']; label: string }[] = [
  { value: 'EQ', label: '=' },
  { value: 'NE', label: '≠' },
  { value: 'LIKE', label: '包含' },
  { value: 'GT', label: '>' },
  { value: 'GE', label: '≥' },
  { value: 'LT', label: '<' },
  { value: 'LE', label: '≤' },
  { value: 'IS_NULL', label: '为空' },
  { value: 'IS_NOT_NULL', label: '不为空' },
]

/**
 * 数据浏览页：像 phpMyAdmin 一样「选库 → 选表 → 看数据 / 看结构」。
 *
 * <p>数据是分页真查的（带筛选与排序）；结构含字段 / 索引 / 建表语句。
 * 权限由后端按端点闸（结构 DMS_SCHEMA_VIEW、数据 DMS_DATA_VIEW）。
 */
export default function BrowserPage() {
  const { message } = AntdApp.useApp()
  const [sources, setSources] = useState<ExplorerSource[]>([])
  const [sourceId, setSourceId] = useState<string | null>(null)
  const [schemas, setSchemas] = useState<SchemaNode[]>([])
  const [schema, setSchema] = useState<string | null>(null)
  const [kind, setKind] = useState<string>('TABLE')
  const [tables, setTables] = useState<SchemaNode[]>([])
  const [table, setTable] = useState<string | null>(null)
  const [loadingTrees, setLoadingTrees] = useState(false)

  // 数据页签
  const [page, setPage] = useState<TableQuery>({
    schema: '', table: '', page: 1, size: 50, countTotal: true,
  })
  const [pageData, setPageData] = useState<Awaited<ReturnType<typeof tableRows>> | null>(null)
  const [rowsLoading, setRowsLoading] = useState(false)
  const [filterColumn, setFilterColumn] = useState<string | undefined>()
  const [filterOp, setFilterOp] = useState<TableFilter['op']>('EQ')
  const [filterValue, setFilterValue] = useState('')

  // 结构页签
  const [detail, setDetail] = useState<TableDetail | null>(null)

  useEffect(() => {
    listExplorerSources()
      .then((list) => {
        setSources(list)
        if (list.length > 0) setSourceId((cur) => cur ?? list[0].id)
      })
      .catch((e: any) => message.error(e?.message || '加载数据源失败'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const reloadSchemas = useCallback(async (sid: string) => {
    setLoadingTrees(true)
    try {
      const list = await listSchemas(sid)
      setSchemas(list)
      setSchema(list.length > 0 ? list[0].name : null)
    } catch (e: any) {
      message.error(e?.message || '加载库清单失败')
      setSchemas([])
      setSchema(null)
    } finally {
      setLoadingTrees(false)
    }
  }, [message])

  const reloadTables = useCallback(async (sid: string, sch: string | null, k: string) => {
    if (!sch) {
      setTables([])
      return
    }
    setLoadingTrees(true)
    try {
      setTables(await listTables(sid, sch, k === 'ALL' ? undefined : k))
    } catch (e: any) {
      message.error(e?.message || '加载表清单失败')
      setTables([])
    } finally {
      setLoadingTrees(false)
    }
  }, [message])

  useEffect(() => {
    if (sourceId) void reloadSchemas(sourceId)
  }, [sourceId, reloadSchemas])

  useEffect(() => {
    if (sourceId) void reloadTables(sourceId, schema, kind)
  }, [sourceId, schema, kind, reloadTables])

  const loadRows = useCallback(async (q: TableQuery) => {
    if (!sourceId || !q.table) return
    setRowsLoading(true)
    try {
      setPageData(await tableRows(sourceId, q))
    } catch (e: any) {
      message.error(e?.message || '读表数据失败')
      setPageData(null)
    } finally {
      setRowsLoading(false)
    }
  }, [sourceId, message])

  useEffect(() => {
    if (sourceId && table && schema) {
      const q: TableQuery = { schema, table, page: 1, size: 50, countTotal: true }
      setPage(q)
      setDetail(null)
      void loadRows(q)
      tableDetail(sourceId, schema, table).then(setDetail).catch(() => setDetail(null))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceId, table, schema])

  const currentColumns = useMemo(() => detail?.columns ?? [], [detail])

  const applyFilter = () => {
    if (!sourceId || !table || !schema) return
    const filters: TableFilter[] = []
    if (filterColumn && (filterOp === 'IS_NULL' || filterOp === 'IS_NOT_NULL' || filterValue !== '')) {
      filters.push({ column: filterColumn, op: filterOp, value: filterValue === '' ? undefined : filterValue })
    }
    const q: TableQuery = { schema, table, page: 1, size: page.size, filters, countTotal: true }
    setPage(q)
    void loadRows(q)
  }

  const clearFilter = () => {
    setFilterColumn(undefined)
    setFilterOp('EQ')
    setFilterValue('')
    if (!sourceId || !table || !schema) return
    const q: TableQuery = { schema, table, page: 1, size: page.size, countTotal: true }
    setPage(q)
    void loadRows(q)
  }

  const gotoPage = (n: number, size: number) => {
    if (!sourceId || !table || !schema) return
    const q: TableQuery = { ...page, schema, table, page: n, size }
    setPage(q)
    void loadRows(q)
  }

  const sortBy = (col: string) => {
    if (!sourceId || !table || !schema) return
    const next: TableQuery = {
      ...page, schema, table,
      sortColumn: col,
      sortOrder: page.sortColumn === col && page.sortOrder === 'ASC' ? 'DESC' : 'ASC',
    }
    setPage(next)
    void loadRows(next)
  }

  const dataColumns: TableColumnsType<string[]> = useMemo(
    () =>
      (pageData?.columns ?? []).map((c) => ({
        title: (
          <a onClick={() => sortBy(c)} title="点击按该列排序">
            {c}
            {page.sortColumn === c ? (page.sortOrder === 'ASC' ? ' ↑' : ' ↓') : ''}
          </a>
        ),
        dataIndex: c,
        key: c,
        ellipsis: true,
        render: (_: unknown, row: string[]) => row[pageData!.columns.indexOf(c)],
      })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [pageData, page.sortColumn, page.sortOrder],
  )

  const columnRows: TableColumnsType<ColumnMeta> = useMemo(
    () => [
      { title: '字段', dataIndex: 'name', key: 'name' },
      { title: '类型', key: 'type', render: (_: unknown, c) => `${c.typeName}${c.precision > 0 ? `(${c.precision}${c.scale > 0 ? `,${c.scale}` : ''})` : ''}` },
      { title: '可空', key: 'nullable', render: (_: unknown, c) => (c.nullable ? 'YES' : 'NO') },
      { title: '键', key: 'pk', render: (_: unknown, c) => (c.primaryKey ? 'PK' : c.autoIncrement ? 'AI' : '') },
      { title: '默认值', dataIndex: 'defaultValue', key: 'defaultValue' },
      { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
    ],
    [],
  )

  const indexRows: TableColumnsType<IndexMeta> = useMemo(
    () => [
      { title: '索引名', dataIndex: 'name', key: 'name' },
      { title: '列', dataIndex: 'columns', key: 'columns' },
      { title: '唯一', key: 'nonUnique', render: (_: unknown, i) => (i.nonUnique ? 'NO' : 'YES') },
    ],
    [],
  )

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card size="small">
        <Space wrap>
          <span>数据源</span>
          <Select
            style={{ minWidth: 220 }}
            value={sourceId ?? undefined}
            onChange={(v) => { setSourceId(v); setTable(null); setPageData(null); setDetail(null) }}
            options={sources.map((s) => ({ value: s.id, label: `${s.name} (${s.id})` }))}
            placeholder="选择数据源"
          />
          <span>库</span>
          <Select
            style={{ minWidth: 180 }}
            value={schema ?? undefined}
            onChange={(v) => { setSchema(v); setTable(null) }}
            options={schemas.map((s) => ({ value: s.name, label: s.name }))}
            placeholder="选择库"
            notFoundContent={loadingTrees ? <Spin size="small" /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />}
          />
          <Segmented
            value={kind}
            onChange={(v) => setKind(v as string)}
            options={[
              { value: 'TABLE', label: '表' },
              { value: 'VIEW', label: '视图' },
              { value: 'ALL', label: '全部' },
            ]}
          />
        </Space>
      </Card>

      <Card
        size="small"
        title={table ? `${schema}.${table}` : '选择一张表'}
        extra={
          table && (
            <Button
              icon={<ReloadOutlined />}
              size="small"
              onClick={() => pageData && void loadRows({ ...page, schema: schema!, table })}
            >
              刷新
            </Button>
          )
        }
      >
        <Table
          size="small"
          rowKey="name"
          loading={loadingTrees}
          columns={[
            { title: '名称', dataIndex: 'name', key: 'name' },
            { title: '类型', dataIndex: 'kind', key: 'kind', width: 80 },
            { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
          ]}
          dataSource={tables}
          pagination={{ pageSize: 15, size: 'small', hideOnSinglePage: true }}
          rowClassName={(r) => (r.name === table ? 'ant-table-row-selected' : '')}
          onRow={(r) => ({ onClick: () => setTable(r.name), style: { cursor: 'pointer' } })}
          locale={{ emptyText: '该库下没有表' }}
          scroll={{ y: 240 }}
        />
      </Card>

      {table && schema && sourceId && (
        <Card size="small">
          <Tabs
            items={[
              {
                key: 'data',
                label: '数据',
                children: (
                  <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    <Space wrap>
                      <Select
                        style={{ minWidth: 140 }}
                        placeholder="筛选字段"
                        allowClear
                        value={filterColumn}
                        onChange={(v) => setFilterColumn(v)}
                        options={currentColumns.map((c) => ({ value: c.name, label: c.name }))}
                      />
                      <Select
                        style={{ width: 96 }}
                        value={filterOp}
                        onChange={(v) => setFilterOp(v)}
                        options={FILTER_OPS}
                        disabled={!filterColumn}
                      />
                      <Input
                        style={{ width: 200 }}
                        placeholder="值"
                        value={filterValue}
                        onChange={(e) => setFilterValue(e.target.value)}
                        onPressEnter={applyFilter}
                        disabled={!filterColumn || filterOp === 'IS_NULL' || filterOp === 'IS_NOT_NULL'}
                      />
                      <Button type="primary" onClick={applyFilter} disabled={!filterColumn}>筛选</Button>
                      <Button onClick={clearFilter}>清空</Button>
                    </Space>
                    <Table
                      size="small"
                      loading={rowsLoading}
                      columns={dataColumns}
                      dataSource={pageData?.rows ?? []}
                      rowKey={(_, i) => String(i)}
                      pagination={false}
                      scroll={{ x: 'max-content', y: 420 }}
                      locale={{ emptyText: '没有数据' }}
                    />
                    <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
                      <span>
                        {pageData
                          ? `共 ${pageData.total >= 0 ? pageData.total : '?'} 行 · 本次 ${pageData.rows.length} 行 · ${pageData.latencyMs} ms`
                          : ''}
                        {pageData?.truncated ? '（超过单页上限，已截断）' : ''}
                      </span>
                      <Pagination
                        size="small"
                        current={pageData?.page ?? 1}
                        pageSize={pageData?.size ?? page.size}
                        total={pageData && pageData.total >= 0 ? pageData.total : (pageData?.rows.length ?? 0) + 1}
                        pageSizeOptions={PAGE_SIZES.map(String)}
                        showSizeChanger
                        onChange={gotoPage}
                      />
                    </Space>
                  </Space>
                ),
              },
              {
                key: 'structure',
                label: '结构',
                children: (
                  <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    <Table
                      size="small"
                      rowKey="name"
                      columns={columnRows}
                      dataSource={currentColumns}
                      pagination={false}
                      locale={{ emptyText: '暂无结构信息' }}
                    />
                    <Typography.Text strong>索引</Typography.Text>
                    <Table
                      size="small"
                      rowKey="name"
                      columns={indexRows}
                      dataSource={detail?.indexes ?? []}
                      pagination={false}
                      locale={{ emptyText: '无索引' }}
                    />
                    {detail?.ddl && (
                      <>
                        <Typography.Text strong>建表语句</Typography.Text>
                        <Input.TextArea readOnly value={detail.ddl} rows={10} style={{ fontFamily: 'monospace' }} />
                      </>
                    )}
                  </Space>
                ),
              },
            ]}
          />
        </Card>
      )}
    </Space>
  )
}
