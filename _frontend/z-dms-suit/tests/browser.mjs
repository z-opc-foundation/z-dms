import { writeFile, readFile } from 'node:fs/promises'
import { browserHarness, evidence, suit } from './browser-harness.mjs'
import { anonymous, authenticated, envelope, menus } from './fixtures.mjs'

const stale = { token: 'unrelated-fixture-token', userInfo: '{"name":"unrelated"}', dmsOperator: 'Fixture operator', dmsUser: '{"name":"Fixture operator"}' }
const button = text => `[...document.querySelectorAll('button')].find(e => e.textContent.replace(/\\s/g,'') === ${JSON.stringify(text.replace(/\s/g, ''))})`
const identity = `document.querySelector('#operator')`
const warning = `document.body.innerText.includes('暂时无法确认访问权限')`
const noBusiness = `!document.querySelector('main, .ant-layout-content, .zfc-mt, .ant-menu-root') && !document.querySelector('#operator')`
const storage = `Object.fromEntries(Object.entries(localStorage))`
const summaries = []

async function runVariant(variant) {
  const h = await browserHarness(variant)
  const { state, evaluate, wait, seed, navigate, click, check, verify, capture } = h
  const scenario = async (name, action) => {
    const start = state.checks.length
    try { await action() } catch (error) {
      console.error(`${variant} ${name}: ${error.stack}`)
      if (error.name !== 'AssertionError' || !state.checks.slice(start).some(c => !c.pass)) check(`scenario: ${name}`, false, error.stack)
      state.scenarioErrors ??= []
      state.scenarioErrors.push({ name, error: error.stack })
      await capture(`failure-${name.replace(/[^a-z0-9]+/gi, '-')}`).catch(() => {})
    }
  }
  const settleResponse = async record => {
    const deadline = Date.now() + 10000
    while (!state.finished.includes(record.networkId)) {
      if (Date.now() > deadline) throw Error('Auth response did not finish')
      await new Promise(resolve => setTimeout(resolve, 30))
    }
    await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))')
  }
  try {
    await scenario('identity and menus', async () => {
      state.authReply = () => ({ body: envelope(anonymous) })
      await seed({ token: stale.token, userInfo: stale.userInfo })
      await navigate('/z-dms/home')
      await wait(`${identity} && location.pathname === '/z-dms/identity'`)
      verify('explicit enabled:false routes missing local identity to form', state.business.length === 0)
      await h.viewport(390, 844)
      await h.fill('#operator', '   ')
      await click(button('进入控制台'))
      await wait(`document.querySelector('.ant-form-item-explain-error')`)
      verify('whitespace rejected without identity writes or business calls', await evaluate(`localStorage.getItem('dmsOperator') === null && !!${identity}`) && state.business.length === 0)
      const geometry = await evaluate(`({ viewport: innerWidth, client: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth, card: document.querySelector('.ant-card').getBoundingClientRect().width })`)
      check('390px identity has no horizontal overflow and card fits', geometry.viewport === 390 && geometry.scroll === geometry.client && geometry.card <= 358, geometry)
      await capture('identity-390')
      await h.fill('#operator', '  Fixture operator  ')
      await click(button('进入控制台'))
      await wait(`location.pathname === '/z-dms/sources' && document.body.innerText.includes('Fixture source')`)
      verify('identity submit trims and saves custom keys', await evaluate(`localStorage.getItem('dmsOperator') === 'Fixture operator' && JSON.parse(localStorage.getItem('dmsUser')).name === 'Fixture operator'`))
      await h.viewport()
      verify('business requests carry operator and existing bearer token', state.business.length > 0 && state.business.every(r => r.headers['X-Operator'] === 'Fixture operator' && r.headers.Authorization === 'Bearer unrelated-fixture-token'))
      const modernLayout = await evaluate(`!!document.querySelector('.zfc-mt') && !document.querySelector('.ant-menu-dark')`)
      check('new common menu delivered', modernLayout)
      for (const [path, label, marker] of menus) {
        const expression = modernLayout
          ? `document.querySelector('a[href="/z-dms/${path}"]')`
          : `[...document.querySelectorAll('.ant-menu-item')].find(e => e.textContent.trim() === ${JSON.stringify(label)})`
        await click(expression)
        const active = modernLayout
          ? `document.querySelector('a[href="/z-dms/${path}"]')?.getAttribute('aria-current') === 'page'`
          : `document.querySelector('.ant-menu-item-selected')?.textContent.trim() === ${JSON.stringify(label)}`
        await wait(`location.pathname === '/z-dms/${path}' && document.querySelector('main, .ant-layout-content')?.innerText.includes(${JSON.stringify(marker)}) && (${active})`)
        verify(`menu ${path}: click, rendered content, active state`, await evaluate(active))
        if (path === 'browser') await wait(`document.body.innerText.includes('fixture_table')`)
        if (path === 'console') {
          await click(button('执行历史'))
          await wait(`document.body.innerText.includes('SELECT fixture')`)
          verify('SQL history displays fixture without executing SQL', !state.allIntercepted.some(r => r.url.includes('/console/execute')))
        }
      }
      await capture('seven-menus')
      if (modernLayout) {
        await click(`document.querySelector('[aria-label="收起侧栏"]')`)
        await wait(`document.querySelector('.zfc-sbs.is-collapsed')`)
        await navigate('/z-dms/home')
        await wait(`document.querySelector('.zfc-sbs.is-collapsed')`)
        verify('collapse persists under z-dms key across reload', await evaluate(`localStorage.getItem('zfc-sidebar:z-dms') === '1'`))
        await click(`document.querySelector('[aria-label="展开侧栏"]')`)
        await wait(`document.querySelector('[aria-label="收起侧栏"]')`)
        verify('collapsed menu can expand again', true)
        await h.viewport(390, 844)
        verify('390px home has no page horizontal overflow', await evaluate(`document.documentElement.scrollWidth === document.documentElement.clientWidth`))
        await capture('home-390')
        await h.viewport()
      } else {
        await click(`document.querySelector('[aria-label="menu-fold"]')`)
        await wait(`document.querySelector('.ant-layout-sider-collapsed')`)
        await navigate('/z-dms/home')
        await wait(`document.querySelector('.ant-layout-sider')`)
        check('collapse persists under z-dms key across reload', await evaluate(`!!document.querySelector('.ant-layout-sider-collapsed') && localStorage.getItem('zfc-sidebar:z-dms') === '1'`))
      }
      if (modernLayout) await click(`document.querySelector('[aria-label="用户菜单"]')`)
      else await h.hover(`document.querySelector('.ant-dropdown-trigger')`)
      await click(`[...document.querySelectorAll('.ant-dropdown-menu-item')].find(e => e.textContent.includes('退出登录'))`)
      await wait(`location.pathname === '/z-dms/identity' && ${identity}`)
      const keys = await evaluate(storage)
      check('logout clears dmsOperator and dmsUser only', !keys.dmsOperator && !keys.dmsUser && keys.token === stale.token && keys.userInfo === stale.userInfo, keys)
      await capture('logout')
      await navigate('/z-dms/home')
      await wait(`!!${identity} || !!document.querySelector('main, .ant-layout-content')`)
      check('post-logout protected route returns to identity', await evaluate(`location.pathname === '/z-dms/identity' && !!${identity}`))
      await capture('post-logout-guard')
    })

    const badResponses = [
      ['503', { status: 503, body: { message: 'Fixture unavailable' } }],
      ['401', { status: 401, body: { message: 'Fixture unauthorized' } }],
      ['malformed-null', { body: envelope(null) }],
      ['malformed-HTML', { html: '<html>Fixture invalid auth</html>' }],
      ['malformed-permissions', { body: envelope({ ...anonymous, permissions: {} }) }],
      ['missing-enabled', { body: envelope({ authenticated: false, degraded: false, permissions: [] }) }],
      ['network', { network: true }],
      ['degraded', { body: envelope({ ...anonymous, degraded: true }) }],
      ['unsafe-login', { body: envelope({ ...authenticated, authenticated: false, loginUrl: 'javascript:alert(1)' }) }],
    ]
    // Both routes matter: direct /identity must not bypass the root gate.
    for (const [name, reply] of badResponses) {
      for (const path of ['/z-dms/home', '/z-dms/identity']) {
        await scenario(`${name}-${path.split('/').at(-1)}`, async () => {
          state.authReply = () => reply
          await seed(stale)
          await navigate(path)
          await wait(warning)
          verify(`${name} ${path}: retry only, no shell/form/business calls`, await evaluate(`${noBusiness} && !!(${button('重新加载')})`) && state.business.length === 0)
          verify(`${name} ${path}: stale identities and unrelated keys preserved`, JSON.stringify(await evaluate(storage)) === JSON.stringify(stale) || await evaluate(`localStorage.getItem('token') === ${JSON.stringify(stale.token)} && localStorage.getItem('userInfo') === ${JSON.stringify(stale.userInfo)} && localStorage.getItem('dmsOperator') === 'Fixture operator' && localStorage.getItem('dmsUser') === ${JSON.stringify(stale.dmsUser)}`))
          if (path.endsWith('/home')) {
            if (name === '503') await capture('503-gate')
            state.authReply = () => ({ body: envelope(anonymous) })
            await click(button('重新加载'))
            await wait(`!!document.querySelector('main, .ant-layout-content') && document.querySelector('.ant-statistic-content')?.textContent === '1'`)
            verify(`${name}: retry success mounts business after recovery`, state.business.length > 0)
          }
        })
      }
    }

    await scenario('SSO cannot bypass', async () => {
      state.ssoUrl = 'https://sso.example.invalid/authorize?client=dms-fixture&return=%2Fz-dms%2Fhome'
      state.authReply = () => ({ body: envelope({ ...authenticated, authenticated: false, loginUrl: state.ssoUrl }) })
      await seed(stale)
      await navigate('/z-dms/identity')
      await wait(`document.body.innerText.includes('尚未登录')`)
      verify('SSO unauthenticated identity URL cannot bypass gate', await evaluate(noBusiness) && state.business.length === 0)
      await click(button('去登录'))
      await wait(`document.body.innerText.includes('Fixture SSO destination')`)
      verify('login button navigates to exact server-provided SSO URL (intercepted)', state.ssoVisited === state.ssoUrl)
      state.authReply = () => ({ body: envelope({ ...authenticated, authenticated: false, loginUrl: null }) })
      await seed(stale)
      await navigate('/z-dms/identity')
      await wait(`!!(${button('刷新重试')})`)
      const before = state.auth.length
      await click(button('刷新重试'))
      await wait(`document.body.innerText.includes('尚未登录') && !!(${button('刷新重试')})`)
      // Wait for the replacement document's probe, not the old DOM.
      const deadline = Date.now() + 10000
      while (state.auth.length === before && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 50))
      verify('missing SSO URL offers actual reload; no identity fallback', state.auth.length > before && await evaluate(noBusiness) && state.business.length === 0)
    })

    await scenario('permissions and display', async () => {
      for (const [name, info, visible, display] of [
        ['source-only / literal user is not special', { ...authenticated, display: 'user', permissions: ['DMS_SOURCE_VIEW'] }, ['首页', '数据源', '组件文档'], 'user'],
        ['no permissions/no display', { ...authenticated, permissions: [], display: null }, ['首页', '组件文档'], '当前用户'],
        ['global admin with literal special display', { ...authenticated, display: '<user>&"fixture' }, menus.map(m => m[1]), '<user>&"fixture'],
      ]) {
        state.authReply = () => ({ body: envelope(info) })
        await seed({})
        await navigate('/z-dms/identity')
        await wait(`location.pathname === '/z-dms/home' && !!document.querySelector('main, .ant-layout-content')`)
        const labels = await evaluate(`[...document.querySelectorAll('.zfc-mt a, .ant-menu-item')].map(e => e.textContent.trim())`)
        verify(`${name}: exact permission-filtered menu`, JSON.stringify(labels) === JSON.stringify(visible), labels)
        verify(`${name}: authenticated without local user; display rendered safely`, await evaluate(`!document.querySelector('#operator') && document.querySelector('header').textContent.includes(${JSON.stringify(display)}) && !document.querySelector('user') && localStorage.getItem('dmsOperator') === null`))
      }
    })

    await scenario('StrictMode stale success', async () => {
      state.authReply = index => index === 0 ? null : { status: 503, body: { message: 'Current probe failed' } }
      await seed(stale)
      await navigate('/z-dms/home')
      await wait(warning)
      verify('real StrictMode starts two probes', state.auth.length === 2)
      const first = state.auth[0]
      await h.fulfill(first.requestId, { body: envelope(anonymous) })
      await settleResponse(first)
      verify('late success from cleaned-up StrictMode effect cannot unlock current error', await evaluate(`${warning} && ${noBusiness}`) && state.business.length === 0)
    })

    await scenario('StrictMode loading and stale error after retry', async () => {
      state.authReply = () => null
      await seed(stale)
      await navigate('/z-dms/identity')
      await wait(`document.querySelector('[role="status"][aria-label="正在确认访问权限"]')`)
      const deadline = Date.now() + 10000
      while (state.auth.length < 2 && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 30))
      verify('loading blocks business and manual identity despite stale local keys', state.auth.length === 2 && state.business.length === 0 && await evaluate(noBusiness))
      await capture('loading-gate')
      await h.fulfill(state.auth[1].requestId, { body: envelope({ ...anonymous, degraded: true }) })
      await wait(warning)
      await click(button('重新加载'))
      await wait(`document.querySelector('[aria-label="正在确认访问权限"]')`)
      verify('retry immediately returns to closed loading gate', await evaluate(noBusiness) && state.business.length === 0)
      const retryDeadline = Date.now() + 10000
      while (state.auth.length < 3 && Date.now() < retryDeadline) await new Promise(resolve => setTimeout(resolve, 30))
      verify('retry starts a fresh probe', state.auth.length === 3)
      await h.fulfill(state.auth[2].requestId, { body: envelope(authenticated) })
      await wait(`location.pathname === '/z-dms/home' && !!document.querySelector('header')`)
      await h.fulfill(state.auth[0].requestId, { status: 401, body: { message: 'Late obsolete failure' } })
      await settleResponse(state.auth[0])
      verify('late rejected StrictMode probe ignored after retry success', await evaluate(`document.querySelector('header').textContent.includes('Fixture SSO user') && !document.querySelector('#operator') && !(${warning})`))
    })

    await scenario('network isolation proof', async () => {
      verify('no API request escaped browser interception into Vite proxy', state.escapedToVite.length === 0, state.escapedToVite)
      verify('no unexpected business/external requests attempted', state.blocked.length === 0, state.blocked)
      verify('no writes, connection probes, or SQL executions requested', state.allIntercepted.every(r => r.method === 'GET') && !state.allIntercepted.some(r => /\/execute|\/acquire|test-connection/.test(r.url)))
      verify('only local Vite HMR WebSockets opened', state.webSockets.every(url => { const u = new URL(url); return u.origin === 'ws://127.0.0.1:5323' && u.pathname === '/' }), state.webSockets)
      verify('no runtime exceptions', state.errors.length === 0, state.errors)
      verify('expired interception IDs correspond only to browser-canceled requests', state.expiredInterceptions.every(r => state.failedRequests.some(f => f.requestId === r.networkId && f.canceled === true)), state.expiredInterceptions)
      // Test the guards themselves after app assertions; these attempts must be blocked.
      await evaluate(`fetch('/api/dms/unknown-test-endpoint').catch(() => null)`)
      verify('unknown endpoint demonstrably fails closed', state.blocked.some(r => r.url.endsWith('/api/dms/unknown-test-endpoint')) && state.escapedToVite.length === 0)
      verify('non-HMR sockets are blocked before connection', await evaluate(`(() => { try { new WebSocket('wss://blocked.example.invalid/business'); } catch {} return window.__blockedSockets.length === 1; })()`))
      verify('service-worker registration disabled', await evaluate(`navigator.serviceWorker.register('/fixture-worker.js').catch(() => window.__blockedWorkers.length === 1)`))
    })
  } finally {
    const result = { ...state, authReply: undefined, counts: { passed: state.checks.filter(c => c.pass).length, failed: state.checks.filter(c => !c.pass).length, apiIntercepted: state.allIntercepted.length, backendEscapes: state.escapedToVite.length } }
    await writeFile(`${evidence}${variant}-browser-results.json`, JSON.stringify(result, null, 2))
    summaries.push({ variant, ...result.counts })
    await h.shutdown()
  }
}

const installed = JSON.parse(await readFile(`${suit}node_modules/@yuku123/z-frontend-common/package.json`, 'utf8'))
console.log(`Default dependency under test: ${installed.name}@${installed.version}`)
for (const variant of ['LOCAL', 'default']) {
  try { await runVariant(variant) } catch (error) { summaries.push({ variant, blocker: error.stack, failed: 1 }); console.error(error) }
}
await writeFile(`${evidence}browser-summary.json`, JSON.stringify({ installedCommon: installed.version, summaries }, null, 2))
console.log(JSON.stringify(summaries, null, 2))
// Known delivery gaps are real failures, not skipped or reclassified as passes.
if (summaries.some(s => s.failed)) process.exitCode = 1
