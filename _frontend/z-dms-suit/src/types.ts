/**
 * z-dms 控制台的数据契约（与 z-dms-admin 的 web/dto 一一对应）。
 */

/** 一行数据源（含健康态）。 */
export interface SourceItem {
  id: string
  name: string
  type: string
  /** 凭据引用（<group>/<key>）；界面上只有这个，没有口令。 */
  credentialRef?: string | null
  properties: Record<string, string>
  /** UP / DOWN / UNKNOWN */
  health: string
  detail?: string | null
  latencyMs?: number | null
  checkedAt?: number | null
}

/** 测连接回执（真实发起连接后的结论）。 */
export interface ProbeResult {
  id: string
  name: string
  up: boolean
  health: string
  detail?: string | null
  latencyMs?: number | null
  checkedAt?: number | null
  /** 本次测连接的目标（host:port/database 或 jdbc-url），不含任何凭据。 */
  target?: string | null
}

/** 变更流水一条（含前后值 JSON 快照）。 */
export interface ChangeRecord {
  id: number
  datasourceId: string
  changeType: string
  credentialRef?: string | null
  operator?: string | null
  detail?: string | null
  beforeValue?: string | null
  afterValue?: string | null
  changedAt: number
}

/** 审计流水一条（变更与使用合流）。 */
export interface AuditEntry {
  category: 'CHANGE' | 'USAGE'
  datasourceId: string
  action: string
  operator?: string | null
  success: boolean
  detail?: string | null
  latencyMs?: number | null
  occurredAt: number
  beforeValue?: string | null
  afterValue?: string | null
}

/** 登记 / 编辑表单。**没有口令字段** —— 凭据面只有 credentialRef。 */
export interface SourceForm {
  id: string
  name: string
  type: string
  credentialRef?: string
  properties: Record<string, string>
}

/** 前后值快照的解析结果（定义视图）。 */
export interface DefSnapshot {
  id?: string
  name?: string
  type?: string
  credentialRef?: string
  properties?: Record<string, string>
}

/* ────────────────────────── 权限与登录态 ────────────────────────── */

/** /auth/me 回执（权限管控开关 + 我的权限点）。 */
export interface MeInfo {
  /** 权限管控是否开启（false = 免登录模式）。 */
  enabled: boolean
  authenticated: boolean
  display?: string | null
  /** 权限服务不可用被降级（按无权限处理，稍后重试）。 */
  degraded: boolean
  permissions: string[]
  /** 未登录时给的 SSO 登录跳转地址（可能为空）。 */
  loginUrl?: string | null
}

/** 权限点元数据（/auth/points）。 */
export interface PermissionPointInfo {
  code: string
  label: string
  description: string
  sortOrder: number
}

/* ────────────────────────── 数据浏览 ────────────────────────── */

/** 可浏览数据源的最小视图（无凭据引用、无连接属性）。 */
export interface ExplorerSource {
  id: string
  name: string
  type: string
}

/** 库 / 表 / 视图节点。 */
export interface SchemaNode {
  name: string
  kind: 'SCHEMA' | 'TABLE' | 'VIEW'
  remark?: string | null
}

/** 字段元数据。 */
export interface ColumnMeta {
  name: string
  typeName: string
  precision: number
  scale: number
  nullable: boolean
  primaryKey: boolean
  autoIncrement: boolean
  defaultValue?: string | null
  remark?: string | null
  ordinal: number
}

/** 索引元数据。 */
export interface IndexMeta {
  name: string
  columns: string
  nonUnique: boolean
}

/** 表结构（字段 + 索引 + 建表语句；DDL 不支持的库为 null）。 */
export interface TableDetail {
  schema: string
  table: string
  kind: string
  remark?: string | null
  ddl?: string | null
  columns: ColumnMeta[]
  indexes: IndexMeta[]
}

/** 浏览过滤器（值参数化，列名白名单在后端校验）。 */
export interface TableFilter {
  column: string
  op: 'EQ' | 'NE' | 'LIKE' | 'GT' | 'GE' | 'LT' | 'LE' | 'IS_NULL' | 'IS_NOT_NULL'
  value?: string
}

/** 表数据查询（分页 + 排序 + 过滤）。 */
export interface TableQuery {
  schema: string
  table: string
  page: number
  size: number
  sortColumn?: string
  sortOrder?: 'ASC' | 'DESC'
  filters?: TableFilter[]
  countTotal?: boolean
}

/** 表数据分页回执（total=-1 表示总数未知；值统一是展示字符串，二进制有占位标记）。 */
export interface TablePage {
  schema: string
  table: string
  columns: string[]
  rows: string[][]
  total: number
  page: number
  size: number
  truncated: boolean
  latencyMs: number
}

/* ────────────────────────── SQL 控制台 ────────────────────────── */

/** 控制台能力位（服务端写开关、行数/超时/长度上限）。 */
export interface ConsoleCapabilities {
  writeEnabled: boolean
  maxRows: number
  maxSqlLength: number
  timeoutSeconds: number
}

/** SQL 执行回执（查询给 columns/rows，写给 updateCount）。 */
export interface SqlExecutionView {
  sourceId: string
  digest: string
  columns: string[]
  rows: string[][]
  hasResultSet: boolean
  updateCount: number
  truncated: boolean
  latencyMs: number
  statementKind: 'READ' | 'WRITE'
}

/** SQL 执行历史一条。 */
export interface SqlHistoryRecord {
  id: number
  datasourceId: string
  operator: string
  sqlDigest?: string | null
  sqlText: string
  statementKind: 'READ' | 'WRITE'
  success: boolean
  rowCount: number
  latencyMs?: number | null
  errorMessage?: string | null
  executedAt: number
}
