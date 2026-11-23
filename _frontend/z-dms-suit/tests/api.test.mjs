import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import ts from 'typescript'
import axios from 'axios'
import { anonymous, authenticated, envelope } from './fixtures.mjs'

const suit = new URL('../', import.meta.url)
// Execute complete source modules, not copied fetchMe/unwrapResponse logic.
function execute(url, imports, globals) {
  const output = ts.transpileModule(readFileSync(url, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true },
    fileName: fileURLToPath(url),
  }).outputText
  const exports = {}
  vm.runInNewContext(output, {
    exports, require: id => { assert.ok(id in imports, `Unexpected import: ${id}`); return imports[id] },
    URL, console, ...globals,
  }, { filename: fileURLToPath(url) })
  return exports
}

function harness(commonUrl, reply) {
  const initial = { token: 'unrelated-fixture-token', userInfo: '{"name":"unrelated"}', dmsOperator: 'Fixture operator', dmsUser: '{"name":"Fixture operator"}' }
  const storage = new Map(Object.entries(initial))
  const localStorage = { getItem: k => storage.get(k) ?? null, removeItem: k => storage.delete(k), setItem: (k, v) => storage.set(k, String(v)) }
  const window = { location: { href: 'http://fixture.invalid/z-dms/home' } }
  const requests = []
  const adapter = client => async config => {
    requests.push({ client, config })
    if (reply.network) throw new axios.AxiosError('Fixture network failure', 'ERR_NETWORK', config)
    const response = { data: reply.body, status: reply.status ?? 200, statusText: 'Fixture', config, headers: {} }
    if (!config.validateStatus(response.status)) throw new axios.AxiosError('Fixture HTTP failure', 'ERR_BAD_RESPONSE', config, null, response)
    return response
  }
  const isolated = axios.create({ adapter: adapter('isolated') })
  isolated.create = config => axios.create({ ...config, adapter: adapter('shared') })
  const globals = { localStorage, window }
  const common = execute(commonUrl, { axios: isolated, antd: { message: {} } }, globals)
  const api = execute(new URL('src/api.ts', suit), { axios: isolated, '@yuku123/z-frontend-common': common }, globals)
  return { ...api, requests, storage, initial, window }
}

const variants = [
  ['default-common-1.2.0', new URL('node_modules/@yuku123/z-frontend-common/src/services/request.js', suit)],
  ['LOCAL-common', new URL('../../../z-frontend-common/src/services/request.js', suit)],
]
for (const [variant, commonUrl] of variants) {
  test(variant, async t => {
    assert.ok(existsSync(commonUrl), `Required implementation missing: ${commonUrl}`)
    for (const [name, value, wrap] of [
      ['explicit disabled anonymous', anonymous, false],
      ['wrapped disabled anonymous', anonymous, true],
      ['authenticated with permissions', authenticated, true],
      ['degraded passes contract for root gate', { ...anonymous, degraded: true }, true],
      ['safe HTTPS SSO URL', { ...authenticated, authenticated: false, loginUrl: 'https://sso.example.invalid/login?return=%2Fz-dms' }, true],
      ['safe HTTP SSO URL', { ...anonymous, loginUrl: 'http://sso.example.invalid/' }, false],
      ['same-origin relative login URL', { ...anonymous, loginUrl: '/sso/login' }, false],
      ['optional null display and login URL', { ...anonymous, display: null, loginUrl: null }, false],
    ]) {
      await t.test(name, async () => {
        const h = harness(commonUrl, { body: wrap ? envelope(value) : value })
        assert.deepEqual(await h.fetchMe(), value)
        assert.equal(h.requests.length, 1)
        const { config, client } = h.requests[0]
        assert.equal(client, 'isolated')
        assert.equal(config.method, 'get')
        assert.equal(config.url, '/api/dms/auth/me')
        assert.equal(config.timeout, 20000)
        assert.equal(config.headers.get('Authorization'), 'Bearer unrelated-fixture-token')
        assert.equal(config.headers.get('X-Operator'), 'Fixture operator')
      })
    }
    const malformed = [
      ['null', null], ['HTML', '<html>not auth JSON</html>'], ['array', []],
      ['missing enabled', { authenticated: false, degraded: false, permissions: [] }],
      ['missing authenticated', { enabled: false, degraded: false, permissions: [] }],
      ['missing degraded', { enabled: false, authenticated: false, permissions: [] }],
      ['nonboolean enabled', { ...anonymous, enabled: 'false' }],
      ['nonboolean authenticated', { ...anonymous, authenticated: 1 }],
      ['nonboolean degraded', { ...anonymous, degraded: 0 }],
      ['permissions missing', { enabled: false, authenticated: false, degraded: false }],
      ['permissions object', { ...anonymous, permissions: {} }],
      ['permissions mixed elements', { ...anonymous, permissions: ['DMS_SOURCE_VIEW', 3] }],
      ['display object', { ...anonymous, display: {} }],
      ['login URL object', { ...anonymous, loginUrl: {} }],
    ]
    const failures = [
      ...malformed.map(([name, body]) => [name, { body: envelope(body) }, /权限接口返回的数据不完整/]),
      ...['javascript:alert(1)', 'data:text/html,fixture', 'file:///fixture', 'ftp://fixture.invalid', 'vbscript:fixture'].map(loginUrl => [loginUrl.split(':')[0], { body: envelope({ ...anonymous, loginUrl }) }, /登录地址不可用/]),
      ['invalid URL syntax', { body: { ...anonymous, loginUrl: 'https://[' } }, /Invalid URL/],
      ['wrapped application error', { body: { code: 503, data: null, message: 'Fixture envelope rejection' } }, /Fixture envelope rejection/],
      ...[401, 503].map(status => [`HTTP ${status}`, { status, body: { message: `Fixture status ${status}` } }, new RegExp(`Fixture status ${status}`)]),
      ['network failure', { network: true }, /Fixture network failure/],
    ]
    for (const [name, reply, error] of failures) {
      await t.test(`reject ${name}; preserve every local key and URL`, async () => {
        const h = harness(commonUrl, reply)
        await assert.rejects(h.fetchMe(), error)
        assert.deepEqual(Object.fromEntries(h.storage), h.initial)
        assert.equal(h.window.location.href, 'http://fixture.invalid/z-dms/home')
        assert.equal(h.requests.length, 1)
        assert.equal(h.requests[0].client, 'isolated')
      })
    }
    await t.test('no identity produces no Authorization or X-Operator', async () => {
      const h = harness(commonUrl, { body: anonymous })
      h.storage.clear()
      await h.fetchMe()
      assert.equal(h.requests[0].config.headers.has('Authorization'), false)
      assert.equal(h.requests[0].config.headers.has('X-Operator'), false)
    })
    await t.test('control: shared business client really redirects on 401', async () => {
      const h = harness(commonUrl, { status: 401, body: { message: 'Fixture unauthorized' } })
      await assert.rejects(h.listSources())
      assert.equal(h.requests[0].client, 'shared')
      assert.equal(h.storage.has('token'), false)
      assert.equal(h.window.location.href, '/z-dms/identity')
    })
  })
}
