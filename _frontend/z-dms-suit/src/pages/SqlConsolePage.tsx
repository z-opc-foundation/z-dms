import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { CaretRightOutlined, HistoryOutlined } from '@ant-design/icons'
import { consoleCapabilities, executeSql, listExplorerSources, listSqlHistory } from '../api'
import type {
  ConsoleCapabilities,
  ExplorerSource,
  SqlExecutionView,
  SqlHistoryRecord,
} from '../types'

const HISTORY_TABLE_COLUMNS: TableColumnsType<SqlHistoryRecord> = [
  {
    title: '时间',
    key: 'executedAt',
    width: 160,
    render: (_: unknown, r) => new Date(r.executedAt).toLocaleString(),
  },
  { title: '数据源', dataIndex: 'datasourceId', key: 'datasourceId', width: 140, ellipsis: true },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 120, ellipsis: true },
  {
    title: '类型',
    dataIndex: 'statementKind',
    key: 'statementKind',
    width: 80,
    render: (k: string) => (k === 'WRITE' ? <Tag color="orange">写</Tag> : <Tag color="blue">读</Tag>),
  },
  { title: '语句', dataIndex: 'sqlText', key: 'sqlText', ellipsis: true },
  {
    title: '结果',
    key: 'success',
    width: 90,
    render: (_: unknown, r) => (r.success ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>),
  },
  { title: '耗时', key: 'latency', width: 90, render: (_: unknown, r) => (r.latencyMs != null ? `${r.latencyMs} ms` : '-') },
]

/**
 * SQL 控制台：选数据源 → 写语句 → 真执行。
 *
 * <p>服务端护栏：单语句 / 只读白名单（写要服务端开开关 + DMS_SQL_WRITE 权限点）/
 * 超时 / 行数上限 / 长度上限。每次执行都进执行历史（谁的、何时、成败、耗时）。
 */
export default function SqlConsolePage() {
  const { message } = AntdApp.useApp()
  const [sources, setSources] = useState<ExplorerSource[]>([])
  const [sourceId, setSourceId] = useState<string | null>(null)
  const [caps, setCaps] = useState<ConsoleCapabilities | null>(null)
  const [sql, setSql] = useState('SELECT 1')
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState<SqlExecutionView | null>(null)
  const [history, setHistory] = useState<SqlHistoryRecord[]>([])
  const [historyOpen, setHistoryOpen] = useState(false)

  useEffect(() => {
    listExplorerSources()
      .then((list) => {
        setSources(list)
        if (list.length > 0) setSourceId((cur) => cur ?? list[0].id)
      })
      .catch((e: any) => message.error(e?.message || '加载数据源失败'))
    consoleCapabilities().then(setCaps).catch(() => setCaps(null))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const reloadHistory = useCallback(() => {
    listSqlHistory(undefined, 50)
      .then(setHistory)
      .catch(() => setHistory([]))
  }, [])

  useEffect(() => {
    if (historyOpen) reloadHistory()
  }, [historyOpen, reloadHistory])

  const run = async () => {
    if (!sourceId) {
      message.warning('先选择数据源')
      return
    }
    const text = sql.trim()
    if (!text) {
      message.warning('SQL 不能为空')
      return
    }
    setRunning(true)
    setResult(null)
    try {
      const view = await executeSql(sourceId, text)
      setResult(view)
    } catch (e: any) {
      message.error(e?.message || '执行失败')
    } finally {
      setRunning(false)
    }
  }

  const resultColumns: TableColumnsType<string[]> = useMemo(
    () =>
      (result?.columns ?? []).map((c) => ({
        title: c,
        key: c,
        dataIndex: c,
        ellipsis: true,
        render: (_: unknown, row: string[]) => row[result!.columns.indexOf(c)],
      })),
    [result],
  )

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card size="small">
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Space wrap>
            <span>数据源</span>
            <Select
              style={{ minWidth: 220 }}
              value={sourceId ?? undefined}
              onChange={setSourceId}
              options={sources.map((s) => ({ value: s.id, label: `${s.name} (${s.id})` }))}
              placeholder="选择数据源"
            />
            {caps && (
              <>
                <Tag color={caps.writeEnabled ? 'green' : 'default'}>
                  写执行{caps.writeEnabled ? '已开启' : '关闭'}
                </Tag>
                <Tag>行数上限 {caps.maxRows}</Tag>
                <Tag>超时 {caps.timeoutSeconds}s</Tag>
                <Tag>长度上限 {caps.maxSqlLength}</Tag>
              </>
            )}
          </Space>
          {caps && !caps.writeEnabled && (
            <Alert
              type="info"
              showIcon
              message="只读模式"
              description="服务端未开启写执行（z.dms.console.write-enabled=false），仅允许 SELECT / SHOW / DESC / EXPLAIN / WITH 查询。"
            />
          )}
          <Input.TextArea
            value={sql}
            onChange={(e) => setSql(e.target.value)}
            rows={8}
            placeholder="输入一条 SQL（单语句；查询无需权限说明，写语句需要服务端开关 + 写权限点）"
            style={{ fontFamily: 'monospace' }}
            maxLength={caps?.maxSqlLength}
          />
          <Space wrap>
            <Button type="primary" icon={<CaretRightOutlined />} loading={running} onClick={run}>
              执行
            </Button>
            <Button
              icon={<HistoryOutlined />}
              onClick={() => setHistoryOpen((v) => !v)}
            >
              执行历史{historyOpen ? '（收起）' : ''}
            </Button>
            <Typography.Text type="secondary">
              执行留痕：使用流水 + 执行历史（原文与结论）
            </Typography.Text>
          </Space>
        </Space>
      </Card>

      {result && (
        <Card size="small" title={`执行结果 · ${result.statementKind === 'WRITE' ? '写' : '读'} · ${result.latencyMs} ms`}>
          {result.hasResultSet ? (
            <>
              {result.truncated && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={`结果超过上限已截断（显示 ${result.rows.length} 行）`}
                />
              )}
              <Table
                size="small"
                columns={resultColumns}
                dataSource={result.rows}
                rowKey={(_, i) => String(i)}
                pagination={{ pageSize: 50, size: 'small', hideOnSinglePage: true }}
                scroll={{ x: 'max-content', y: 380 }}
                locale={{ emptyText: '查询成功，0 行' }}
              />
            </>
          ) : (
            <Alert
              type="success"
              showIcon
              message={`执行成功：影响 ${result.updateCount} 行（${result.latencyMs} ms）`}
            />
          )}
        </Card>
      )}

      {historyOpen && (
        <Card size="small" title="执行历史（最近 50 条）" extra={<Button size="small" onClick={reloadHistory}>刷新</Button>}>
          <Table
            size="small"
            rowKey="id"
            columns={HISTORY_TABLE_COLUMNS}
            dataSource={history}
            pagination={{ pageSize: 10, size: 'small', hideOnSinglePage: true }}
            expandable={{
              expandedRowRender: (r) => (
                <div>
                  <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}>{r.sqlText}</pre>
                  {r.errorMessage && <Typography.Text type="danger">错误：{r.errorMessage}</Typography.Text>}
                </div>
              ),
              rowExpandable: () => true,
            }}
            locale={{ emptyText: '还没有执行记录' }}
          />
        </Card>
      )}
    </Space>
  )
}
