import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer as netServer } from 'node:net'
import { createServer } from 'vite'
import { businessFixtures, envelope, anonymous } from './fixtures.mjs'

export const suit = fileURLToPath(new URL('../', import.meta.url))
export const evidence = fileURLToPath(new URL('../../../../.scratch/dms-access-review/', import.meta.url))
const origin = 'http://127.0.0.1:5323'
const debuggingPort = 9323
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))

async function requireUnusedPort(port) {
  const server = netServer()
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve) })
  await new Promise(resolve => server.close(resolve))
}

export async function browserHarness(variant) {
  await mkdir(evidence, { recursive: true })
  await requireUnusedPort(5323)
  await requireUnusedPort(debuggingPort)
  const state = { variant, auth: [], business: [], allIntercepted: [], finished: [], failedRequests: [], expiredInterceptions: [], blocked: [], escapedToVite: [], errors: [], console: [], webSockets: [], network: [], checks: [], authReply: () => ({ body: envelope(anonymous) }) }
  let vite, chrome, socket, id = 0
  const pending = new Map()
  const handlers = new Map()
  let call
  const shutdown = async () => {
    socket?.close()
    for (const entry of pending.values()) { clearTimeout(entry.timer); entry.reject(Error('Browser closed')) }
    pending.clear()
    if (chrome && chrome.exitCode === null) {
      const exited = new Promise(resolve => chrome.once('exit', resolve))
      chrome.kill('SIGTERM')
      await exited
    }
    await vite?.close()
  }
  try {
    if (variant === 'LOCAL') process.env.LOCAL_SIBLINGS = '1'
    else delete process.env.LOCAL_SIBLINGS
    vite = await createServer({
      root: suit,
      configFile: `${suit}vite.config.js`,
      // Keep Vite's usual node_modules boundary so React does not re-transform
      // already-optimized vendor bundles; all cache output still stays in scratch.
      cacheDir: `${evidence}node_modules/.vite-${variant}`,
      server: { host: '127.0.0.1', port: 5323, strictPort: true, open: false },
      // Defense in depth: any missed interception must NEVER reach the real proxy.
      plugins: [{ name: 'test-only-block-backend', configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (/^\/api(?:\/|\?|$)/.test(req.url || '')) {
            state.escapedToVite.push({ method: req.method, url: req.url })
            res.statusCode = 599
            res.end('Test firewall: backend access forbidden')
          } else next()
        })
      } }],
    })
    state.config = { configFile: vite.config.configFile, aliases: vite.config.resolve.alias.map(a => ({ find: String(a.find), replacement: a.replacement })), fsAllow: vite.config.server.fs.allow }
    await vite.listen()
    const profile = await mkdtemp(`${evidence}chrome-${variant}-`)
    chrome = spawn(process.env.CHROME_BIN || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', [
      '--headless=new', '--no-first-run', '--no-default-browser-check', '--no-proxy-server',
      '--disable-background-networking', '--disable-component-update', '--disable-sync',
      '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
      `--remote-debugging-port=${debuggingPort}`, `--user-data-dir=${profile}`, 'about:blank',
    ], { stdio: ['ignore', 'ignore', 'pipe'] })
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(Error('Chrome startup timeout')), 20000)
      let text = ''
      chrome.stderr.on('data', data => { text += data; if (text.includes('DevTools listening')) { clearTimeout(timer); resolve() } })
      chrome.once('error', error => { clearTimeout(timer); reject(error) })
      chrome.once('exit', code => { clearTimeout(timer); reject(Error(`Chrome exited ${code}`)) })
    })
    const targets = await (await fetch(`http://127.0.0.1:${debuggingPort}/json/list`)).json()
    socket = new WebSocket(targets.find(p => p.type === 'page').webSocketDebuggerUrl)
    await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
    call = (method, params = {}) => new Promise((resolve, reject) => {
      const requestId = ++id
      const timer = setTimeout(() => { pending.delete(requestId); reject(Error(`${method} timeout`)) }, 25000)
      pending.set(requestId, { resolve, reject, timer })
      socket.send(JSON.stringify({ id: requestId, method, params }))
    })
    socket.onmessage = ({ data }) => {
      const message = JSON.parse(data)
      if (pending.has(message.id)) {
        const entry = pending.get(message.id)
        pending.delete(message.id)
        clearTimeout(entry.timer)
        message.error ? entry.reject(Error(JSON.stringify(message.error))) : entry.resolve(message.result)
      } else if (handlers.has(message.method)) {
        Promise.resolve(handlers.get(message.method)(message.params)).catch(error => {
          // Navigation can cancel a paused request before CDP answers fulfill/continue.
          // Keep evidence and require a matching Network.loadingFailed cancellation;
          // never ignore arbitrary interception failures or application exceptions.
          if (message.method === 'Fetch.requestPaused' && error.message.includes('Invalid InterceptionId.')) {
            state.expiredInterceptions.push({ networkId: message.params.networkId, url: message.params.request.url, error: error.message })
          } else state.errors.push({ harness: error.stack })
        })
      }
    }
    const evaluate = async expression => {
      const result = await call('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture: true })
      if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
      return result.result.value
    }
    const wait = async (expression, timeout = 45000) => {
      const deadline = Date.now() + timeout
      while (Date.now() < deadline) {
        try { if (await evaluate(`Boolean(document.body && (${expression}))`)) return } catch (error) { if (!/context|navigat/i.test(error.message)) throw error }
        await delay(50)
      }
      throw Error(`DOM timeout: ${expression}\n${await evaluate('document.body.innerText').catch(() => '')}`)
    }
    const fulfill = (requestId, reply) => reply.network
      ? call('Fetch.failRequest', { requestId, errorReason: 'ConnectionFailed' })
      : call('Fetch.fulfillRequest', { requestId, responseCode: reply.status || 200, responseHeaders: [{ name: 'Content-Type', value: reply.html ? 'text/html' : 'application/json' }], body: Buffer.from(reply.html ?? JSON.stringify(reply.body)).toString('base64') })
    handlers.set('Fetch.requestPaused', async p => {
      const url = new URL(p.request.url)
      const record = { url: url.href, method: p.request.method, headers: p.request.headers, requestId: p.requestId, networkId: p.networkId }
      if (url.pathname.startsWith('/api/')) state.allIntercepted.push(record)
      if (url.origin === origin && url.pathname === '/api/dms/auth/me' && p.request.method === 'GET') {
        state.auth.push(record)
        const reply = state.authReply(state.auth.length - 1, record)
        if (reply) await fulfill(p.requestId, reply)
      } else if (url.origin === origin && url.pathname in businessFixtures && p.request.method === 'GET') {
        state.business.push(record)
        await fulfill(p.requestId, { body: envelope(businessFixtures[url.pathname]) })
      } else if (url.origin === origin && url.pathname === '/__test_bootstrap') {
        await fulfill(p.requestId, { html: '<!doctype html><title>Isolated fixture setup</title><body>Test bootstrap</body>' })
      } else if (state.ssoUrl && url.href === state.ssoUrl && p.resourceType === 'Document') {
        state.ssoVisited = url.href
        await fulfill(p.requestId, { html: '<!doctype html><link rel="icon" href="data:,"><title>Intercepted SSO</title><body>Fixture SSO destination</body>' })
      } else if (url.origin === origin && p.request.method === 'GET' && (
        (p.resourceType === 'Document' && (url.pathname === '/' || /^\/z-dms\/(identity|home|sources|browser|console|changes|audit|docs)$/.test(url.pathname))) ||
        (['Script', 'Stylesheet', 'Image', 'Font', 'Other'].includes(p.resourceType) && /^\/(?:@vite\/|@react-refresh|@fs\/|@id\/|src\/|node_modules\/|icon\.png$|favicon\.ico$)/.test(url.pathname))
      )) {
        await call('Fetch.continueRequest', { requestId: p.requestId })
      } else {
        state.blocked.push(record)
        await call('Fetch.failRequest', { requestId: p.requestId, errorReason: 'BlockedByClient' })
      }
    })
    handlers.set('Runtime.exceptionThrown', p => state.errors.push(p.exceptionDetails))
    handlers.set('Runtime.consoleAPICalled', p => state.console.push({ type: p.type, args: p.args.map(a => a.value ?? a.description) }))
    handlers.set('Network.requestWillBeSent', p => state.network.push({ url: p.request.url, type: p.type, method: p.request.method }))
    handlers.set('Network.webSocketCreated', p => state.webSockets.push(p.url))
    handlers.set('Network.loadingFinished', p => state.finished.push(p.requestId))
    handlers.set('Network.loadingFailed', p => state.failedRequests.push(p))
    await call('Runtime.enable')
    await call('Page.enable')
    await call('Network.enable')
    await call('Network.setBypassServiceWorker', { bypass: true })
    await call('Network.setCacheDisabled', { cacheDisabled: true })
    await call('Fetch.enable', { patterns: [{ urlPattern: '*', requestStage: 'Request' }] })
    await call('Page.addScriptToEvaluateOnNewDocument', { source: `
      window.__blockedSockets = []; window.__blockedWorkers = [];
      const NativeWebSocket = window.WebSocket;
      window.WebSocket = class extends NativeWebSocket {
        constructor(url, protocols) {
          const target = new URL(url, location.href);
          if (target.origin !== 'ws://127.0.0.1:5323' || target.pathname !== '/' || protocols !== 'vite-hmr') {
            window.__blockedSockets.push(String(url)); throw new Error('Test blocks non-HMR WebSocket');
          }
          super(url, protocols);
        }
      };
      if (navigator.serviceWorker) navigator.serviceWorker.register = (...args) => {
        window.__blockedWorkers.push(String(args[0])); return Promise.reject(new Error('Test blocks service workers'));
      };
    ` })
    await call('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] })
    const viewport = (width = 1280, height = 900) => call('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false })
    await viewport()
    const navigate = async path => {
      const loaded = new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(Error(`Navigation timeout: ${path}`)), 90000)
        handlers.set('Page.loadEventFired', () => { clearTimeout(timer); resolve() })
      })
      const result = await call('Page.navigate', { url: origin + path })
      if (result.errorText) throw Error(result.errorText)
      await loaded
    }
    const seed = async (storage = {}) => {
      await navigate('/__test_bootstrap')
      await evaluate(`localStorage.clear(); for (const [key, value] of Object.entries(${JSON.stringify(storage)})) localStorage.setItem(key, value)`)
      state.auth = []
      state.business = []
    }
    // Use pointer events, not direct invocation of handlers; selectors refer to real DOM.
    const point = async expression => {
      await wait(expression)
      await evaluate(`(${expression}).scrollIntoView({block:'center'})`)
      await wait(`(() => {
        const e = (${expression}); if (!e) return false;
        const r = e.getBoundingClientRect(); const s = getComputedStyle(e);
        return r.width > 0 && r.height > 0 && s.visibility === 'visible' && s.opacity !== '0'
          && e.contains(document.elementFromPoint(r.x+r.width/2, r.y+r.height/2));
      })()`)
      // Ant Dropdown animates its popup; pointer coordinates must settle first.
      return evaluate(`new Promise(resolve => {
        let previous = '', stable = 0;
        const frame = () => {
          const e = (${expression}); if (!e) { resolve(null); return; }
          const r = e.getBoundingClientRect(); const key = [r.x,r.y,r.width,r.height].join(',');
          stable = key === previous ? stable + 1 : 0; previous = key;
          if (stable >= 3) resolve({x:r.x+r.width/2,y:r.y+r.height/2}); else requestAnimationFrame(frame);
        }; requestAnimationFrame(frame);
      })`)
    }
    const click = async expression => {
      const p = await point(expression)
      await call('Input.dispatchMouseEvent', { type: 'mouseMoved', ...p })
      await call('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...p })
      await call('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...p })
    }
    const hover = async expression => call('Input.dispatchMouseEvent', { type: 'mouseMoved', ...await point(expression) })
    const fill = async (selector, value) => {
      await click(`document.querySelector(${JSON.stringify(selector)})`)
      await evaluate(`(() => { const e=document.querySelector(${JSON.stringify(selector)}); e.select(); })()`)
      await call('Input.insertText', { text: value })
    }
    const capture = async name => {
      const shot = await call('Page.captureScreenshot', { format: 'png' })
      await writeFile(`${evidence}${variant}-${name}.png`, Buffer.from(shot.data, 'base64'))
      await writeFile(`${evidence}${variant}-${name}.dom.txt`, await evaluate('document.body.innerText'))
    }
    const check = (name, value, details) => {
      state.checks.push({ name, pass: Boolean(value), ...(details === undefined ? {} : { details }) })
      console.log(`${variant} ${value ? 'PASS' : 'FAIL'} ${name}`)
      return Boolean(value)
    }
    const verify = (name, value, details) => assert.ok(check(name, value, details), name)
    return { state, call, evaluate, wait, fulfill, navigate, viewport, seed, click, hover, fill, capture, check, verify, shutdown }
  } catch (error) { await shutdown(); throw error }
}
