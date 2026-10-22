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
