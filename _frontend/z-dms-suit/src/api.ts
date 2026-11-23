import axios from 'axios'
import { createRequest, unwrapResponse } from '@yuku123/z-frontend-common'
import type {
  AuditEntry,
  ChangeRecord,
  ConsoleCapabilities,
  ExplorerSource,
  MeInfo,
  PermissionPointInfo,
  ProbeResult,
  SchemaNode,
  SourceForm,
  SourceItem,
  SqlExecutionView,
  SqlHistoryRecord,
  TableDetail,
  TablePage,
  TableQuery,
} from './types'

/**
 * z-dms-admin 的 API 适配层。
 *
 * 【为什么带 X-Operator 头】
 * 审计面要能回答「谁」：变更流水与使用流水都记操作人。
 * 操作人由平台 SSO / 网关以 X-Operator 头下发；本控制台本地形态下由
 * 「身份选择」页写入 localStorage，这里在请求拦截器里带上。
 *
 * 【后端返回体】
 * { code, data, message } —— 基座的 unwrapResponse 会按 code 解包，
 * code≠200 直接 reject（错误消息在 message 里）。
 */
const http = createRequest({ baseURL: '/api', timeout: 20000, unauthorizedRedirect: '/z-dms/identity' })

http.interceptors.request.use((config: any) => {
  const operator = localStorage.getItem('dmsOperator')
  if (operator) {
    config.headers = config.headers || {}
    config.headers['X-Operator'] = operator
  }
  return config
})

/** 把后端错误统一成 Error（消息取后端的 message，界面直接显示）。 */
async function call<T>(fn: () => Promise<unknown>): Promise<T> {
  try {
    return (await fn()) as T
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '请求失败'
    throw new Error(msg)
  }
}

/** 全部登记数据源 + 各自健康态（一屏）。 */
export function listSources(): Promise<SourceItem[]> {
  return call<SourceItem[]>(() => http.get('/dms/sources'))
}

/** 凭据引用目录（界面凭据面只有这个下拉选择器）。 */
export function listCredentialRefs(): Promise<string[]> {
  return call<string[]>(() => http.get('/dms/credential-refs'))
}

/** 登记（注册即探活）。 */
export function registerSource(form: SourceForm): Promise<SourceItem> {
  return call<SourceItem>(() => http.post('/dms/sources', form))
}

/** 修改（换绑：旧池显式关闭，新池重新探活）。 */
export function updateSource(id: string, form: SourceForm): Promise<SourceItem> {
  return call<SourceItem>(() => http.put(`/dms/sources/${encodeURIComponent(id)}`, form))
}

/** 摘除。 */
export function removeSource(id: string): Promise<{ removed: string }> {
  return call<{ removed: string }>(() => http.delete(`/dms/sources/${encodeURIComponent(id)}`))
}

/** 测连接（已登记的源）：后端真借一次连接跑 SELECT 1。 */
export function testConnection(id: string): Promise<ProbeResult> {
  return call<ProbeResult>(() => http.post(`/dms/sources/${encodeURIComponent(id)}/test-connection`))
}

/** 测连接（未登记的候选）：后端现场建临时池探一次。 */
export function testCandidate(form: SourceForm): Promise<ProbeResult> {
  return call<ProbeResult>(() => http.post('/dms/sources/test-connection', form))
}

/** 取连接（真取一次连接跑一条 SELECT）：审计流水里的「使用记录」来自这里。 */
export function acquireConnection(id: string): Promise<{
  id: string
  target?: string | null
  latencyMs: number
  rows: number
}> {
  return call(() => http.post(`/dms/sources/${encodeURIComponent(id)}/acquire`))
}

/** 变更历史（带前后值快照）。 */
export function listChanges(datasourceId?: string): Promise<ChangeRecord[]> {
  const q = datasourceId ? `?datasourceId=${encodeURIComponent(datasourceId)}` : ''
  return call<ChangeRecord[]>(() => http.get(`/dms/changes${q}`))
}

/** 审计流水（变更 + 使用）。 */
export function listAudit(datasourceId?: string): Promise<AuditEntry[]> {
  const q = datasourceId ? `?datasourceId=${encodeURIComponent(datasourceId)}` : ''
  return call<AuditEntry[]>(() => http.get(`/dms/audit${q}`))
}

/* ────────────────────────── 权限与登录态 ────────────────────────── */

/** 当前登录态与权限点（未登录也返回 authenticated=false）。 */
export async function fetchMe(): Promise<MeInfo> {
  const token = localStorage.getItem('token')
  const operator = localStorage.getItem('dmsOperator')
  // 身份探测失败必须留在重试页，不能触发共享客户端的自动退出跳转。
  const me = await call<MeInfo>(async () => {
    const response = await axios.get('/api/dms/auth/me', {
      timeout: 20000,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(operator ? { 'X-Operator': operator } : {}),
      },
    })
    return unwrapResponse(response.data)
  })
  if (!me || typeof me.enabled !== 'boolean' || typeof me.authenticated !== 'boolean'
    || typeof me.degraded !== 'boolean' || !Array.isArray(me.permissions)
    || !me.permissions.every(permission => typeof permission === 'string')
    || (me.display != null && typeof me.display !== 'string')
    || (me.loginUrl != null && typeof me.loginUrl !== 'string')) {
    throw new Error('权限接口返回的数据不完整')
  }
  if (me.loginUrl && !['http:', 'https:'].includes(new URL(me.loginUrl, window.location.href).protocol)) {
    throw new Error('登录地址不可用')
  }
  return me
}

/** z-dms 权限点清单（4A 中心申请授权时对照）。 */
export function listPermissionPoints(): Promise<PermissionPointInfo[]> {
  return call<PermissionPointInfo[]>(() => http.get('/dms/auth/points'))
}

/* ────────────────────────── 数据浏览 ────────────────────────── */

/** 可浏览的数据源清单（最小视图：id/name/type）。 */
export function listExplorerSources(): Promise<ExplorerSource[]> {
  return call<ExplorerSource[]>(() => http.get('/dms/explorer/sources'))
}

/** 某数据源的库清单。 */
export function listSchemas(sourceId: string): Promise<SchemaNode[]> {
  return call<SchemaNode[]>(() =>
    http.get(`/dms/explorer/${encodeURIComponent(sourceId)}/schemas`),
  )
}

/** 某库下的表 / 视图清单。 */
export function listTables(sourceId: string, schema: string, kind?: string): Promise<SchemaNode[]> {
  const q = new URLSearchParams({ schema })
  if (kind) q.set('kind', kind)
  return call<SchemaNode[]>(() =>
    http.get(`/dms/explorer/${encodeURIComponent(sourceId)}/tables?${q.toString()}`),
  )
}

/** 表结构（字段 + 索引 + 建表语句）。 */
export function tableDetail(sourceId: string, schema: string, table: string): Promise<TableDetail> {
  const q = new URLSearchParams({ schema, table })
  return call<TableDetail>(() =>
    http.get(`/dms/explorer/${encodeURIComponent(sourceId)}/table?${q.toString()}`),
  )
}

/** 表数据分页（筛选 + 排序）。 */
export function tableRows(sourceId: string, query: TableQuery): Promise<TablePage> {
  return call<TablePage>(() =>
    http.post(`/dms/explorer/${encodeURIComponent(sourceId)}/rows`, query),
  )
}

/* ────────────────────────── SQL 控制台 ────────────────────────── */

/** 控制台能力位（写开关、上限）。 */
export function consoleCapabilities(): Promise<ConsoleCapabilities> {
  return call<ConsoleCapabilities>(() => http.get('/dms/console/capabilities'))
}

/** 执行一条 SQL（真执行；写语句要服务端开开关 + 有写权限点）。 */
export function executeSql(sourceId: string, sql: string, maxRows?: number): Promise<SqlExecutionView> {
  return call<SqlExecutionView>(() =>
    http.post('/dms/console/execute', { sourceId, sql, maxRows }),
  )
}

/** SQL 执行历史。 */
export function listSqlHistory(datasourceId?: string, limit = 50): Promise<SqlHistoryRecord[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  if (datasourceId) q.set('datasourceId', datasourceId)
  return call<SqlHistoryRecord[]>(() => http.get(`/dms/console/history?${q.toString()}`))
}
