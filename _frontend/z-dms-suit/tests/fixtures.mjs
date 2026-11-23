// Synthetic contracts from src/types.ts; no production identifiers or credentials.
export const anonymous = { enabled: false, authenticated: false, degraded: false, permissions: [] }
export const authenticated = { enabled: true, authenticated: true, degraded: false, permissions: ['DMS_GLOBAL_ADMIN'], display: 'Fixture SSO user' }
export const envelope = data => ({ code: 200, data, message: 'fixture only' })
export const source = { id: 'fixture-source', name: 'Fixture source', type: 'MYSQL', properties: {}, health: 'UP', latencyMs: 1, checkedAt: 1700000000000 }
export const businessFixtures = {
  '/api/dms/sources': [source],
  '/api/dms/credential-refs': ['fixture/reference'],
  '/api/dms/changes': [{ id: 1, datasourceId: source.id, changeType: 'REGISTER', operator: 'Fixture operator', changedAt: 1700000000000, beforeValue: null, afterValue: JSON.stringify(source) }],
  '/api/dms/audit': [{ category: 'CHANGE', datasourceId: source.id, action: 'REGISTER', operator: 'Fixture operator', success: true, occurredAt: 1700000000000 }],
  '/api/dms/explorer/sources': [{ id: source.id, name: source.name, type: source.type }],
  '/api/dms/explorer/fixture-source/schemas': [{ name: 'fixture_schema', kind: 'SCHEMA' }],
  '/api/dms/explorer/fixture-source/tables': [{ name: 'fixture_table', kind: 'TABLE' }],
  '/api/dms/console/capabilities': { writeEnabled: false, maxRows: 100, maxSqlLength: 1000, timeoutSeconds: 5 },
  '/api/dms/console/history': [{ id: 1, datasourceId: source.id, operator: 'Fixture operator', sqlText: 'SELECT fixture', statementKind: 'READ', success: true, rowCount: 0, executedAt: 1700000000000 }],
}
export const menus = [
  ['home', '首页', '已登记数据源'],
  ['sources', '数据源', '数据源（组织级登记台账）'],
  ['browser', '数据浏览', '选择一张表'],
  ['console', 'SQL 控制台', '写执行关闭'],
  ['changes', '变更历史', '变更历史（每一次修改的前后值）'],
  ['audit', '审计流水', '审计流水（谁改了什么'],
  ['docs', '组件文档', '组件文档'],
]
