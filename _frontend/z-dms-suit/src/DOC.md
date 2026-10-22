# z-dms-suit

z-dms（组织级数据源管控）的**独立运行壳**（lead 005 §9.1 suit）：把「数据源登记台账 / 测连接 / 变更历史 / 审计流水 /
身份选择」这一整套管控页面收敛成一个 SPA，由 `z-dms-admin` 在同一进程内伺服静态产物（Mode 1 合体）。

- **npm 包名**：`@yuku123/z-dms-suit`（`private: true`，**不发布 npm**，只服务本仓 admin）
- **宿主接入**：无（独立壳）。仓内对外出口是 `src/index.ts`（页面清单 + 文档页）
- **后端**：`z-dms-admin` 的 `/api/dms/**`，基座 `createRequest` 统一解包 `{code, data, message}`
- **技术栈**：React 19 + antd 6 + react-router-dom 7 + TypeScript 5.6 + vite（**应用层**，基座内联进 bundle）

> ⚠ 本仓**没有** `z-dms-component` 包：管控页面代码住在本壳 `src/` 下，
> 因此本文档描述的是**本壳自身的能力**。

---

## 一、能力概览

路由前缀 `/z-dms`，菜单与路由都在 `src/pages-manifest.tsx` 里声明，`src/App.tsx` 读数组自己组装。

| 页面 | 路由 | 源文件 | 能力 |
|------|------|--------|------|
| 数据源 | `/z-dms/sources` | `src/pages/SourcesPage.tsx` | 登记台账（列表 + 健康态 + 延迟 + 探活结论），支持登记 / 编辑 / 测连接 / 摘除 |
| 变更历史 | `/z-dms/changes` | `src/pages/ChangeHistoryPage.tsx` | 每一次修改的前后值逐字段对照（展开行看 diff） |
| 审计流水 | `/z-dms/audit` | `src/pages/AuditPage.tsx` | 变更 + 使用合流，回答「谁在什么时候动了/用了哪个数据源」 |
| 身份选择 | `/z-dms/identity` | `src/IdentityPage.tsx` | 代替口令登录页（**没有口令输入框**），写入 `localStorage.dmsOperator` |
| 组件文档 | `/z-dms/docs` | `src/pages/ComponentDoc.tsx` | 本文件渲染页（左侧 240px TOC + 右侧正文） |

`src/pages/SourceFormDrawer.tsx` 是登记 / 编辑抽屉，被数据源页调用，**不进菜单、不单独占路由**。

## 二、导出面

```ts
// 仓内出口（src/index.ts）
import {menuItems, routes, ComponentDocPage} from './src/index'

// 单个页面
import SourcesPage from './src/pages/SourcesPage'

// 数据契约
import type {SourceItem, SourceForm, ProbeResult, ChangeRecord, AuditEntry} from './src/types'

// API 适配层
import {listSources, testConnection, listChanges, listAudit} from './src/api'
```

`src/index.ts` 是本壳**唯一的 barrel**：把 `src/pages-manifest.tsx` 的清单整份转发，并让文档页以**统一名字**
`ComponentDocPage` 暴露出去（与其它仓「文档页统一叫 `ComponentDocPage`」的口径一致）。

## 三、API 端点

请求实例由基座 `createRequest({baseURL: '/api', timeout: 20000})` 创建，下表路径是**实际调用**的完整路径
（`src/api.ts` 里写的是相对 `/api` 的片段）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/dms/sources` | 全部登记数据源 + 各自健康态（一屏） |
| GET | `/api/dms/credential-refs` | 凭据引用目录（界面凭据面只有这个下拉选择器） |
| POST | `/api/dms/sources` | 登记数据源（**注册即探活**） |
| PUT | `/api/dms/sources/{id}` | 修改数据源（换绑：旧池显式关闭，新池重新探活） |
| DELETE | `/api/dms/sources/{id}` | 摘除数据源，返回 `{removed}` |
| POST | `/api/dms/sources/{id}/test-connection` | 测连接（已登记的源）：后端真借一次连接跑 `SELECT 1` |
| POST | `/api/dms/sources/test-connection` | 测连接（未登记的候选）：后端现场建临时池探一次 |
| POST | `/api/dms/sources/{id}/acquire` | 取连接（真取一次连接跑一条 SELECT）—— **已实现但页面未调用** |
| GET | `/api/dms/changes?datasourceId=…` | 变更历史（带前后值快照），`datasourceId` 可选 |
| GET | `/api/dms/audit?datasourceId=…` | 审计流水（变更 + 使用），`datasourceId` 可选 |

请求拦截器会带上 `X-Operator` 头（值取自 `localStorage.dmsOperator`）—— 审计面要能回答「谁」。
后端响应形如 `{code, data, message}`，基座按 `code` 解包，`code≠200` 直接 reject。

## 四、页面说明

### 4.1 数据源

一屏看到全部登记数据源与各自健康态。**健康态是后端当场探出来的**（逐池真借一次连接跑 `SELECT 1`），
「测连接」按钮也是真发起连接 —— 不是回固定值，结论带真实耗时与根因。

- 列：数据源 ID / 名称 / 类型 / 凭据引用 / 健康态 / 延迟 / 探活结论 / 操作。
- 操作：测连接、编辑（抽屉）、删除（`Popconfirm` 二次确认，提示会留一条 `REMOVED` 变更记录）。
- 「刷新并重新探活」会重新拉全量列表，因此健康态随之刷新。

### 4.2 变更历史

分页展示变更流水，**展开一行就是「改了什么」的逐字段对照**：左边变更前、右边变更后，值不同的字段标红。
后端存的是定义快照 JSON（含 `credentialRef`，**不含任何口令**）；快照里的 `properties` 会被展开成
`properties.xxx` 逐项对照。顶部可按数据源过滤。

### 4.3 审计流水

变更与使用合流在一条流里。「使用」记录回答的是「谁在什么时候用了哪个数据源」——
后端每次测连接 / 取连接都会写一条使用流水（谁、什么时候、结论、耗时）。
顶部可按数据源过滤，也可按类别（`CHANGE` / `USAGE`）过滤。

### 4.4 身份选择

**这不是登录页**，是一个「以什么身份操作」的开关：填一个操作人名字，写入
`localStorage.dmsOperator` 与 `localStorage.dmsUser`，然后进控制台。
生产环境下该身份由平台 SSO / 网关以 `X-Operator` 头下发。

### 4.5 登记 / 编辑抽屉

被数据源页调用。凭据面**只有 `credentialRef` 下拉选择器**，没有任何口令输入框；
口令由 z-mist 按引用取出，只在服务端内存里拼进连接参数。类型下拉里 `MEMORY` / `CSV` / `HTTP` / `SQL`
**置灰**（连接工厂未落地，不给一个「选了必定登记失败」的假选项）。

### 4.6 组件文档

本文件。内容以 `?raw` 在编译期内联，零额外请求；左侧 240px TOC 随滚动联动。

## 五、配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| dev proxy `/api` | `http://localhost:18098` | dev 时前端跑 5183、后端跑 18098 |
| dev server port | `5183` | `vite.config.js` 的 `server.port` |
| vite `base` | `'/'` | **必须与 admin 的 `server.servlet.context-path` 一致**，否则页面能开但 `/assets/*` 全 404 |
| request `baseURL` | `/api` | `createRequest` 的默认值，写在 `src/api.ts` |
| request timeout | `20000` | 同上 |
| `X-Operator` 头 | 取 `localStorage.dmsOperator` | 请求拦截器注入；生产由 SSO / 网关下发 |
| 操作人存储键 | `dmsOperator` | `localStorage`，`ProtectedShell` 据此判断是否放行 |
| 用户信息存储键 | `dmsUser` | `localStorage`，JSON 串 |
| `AppLayout` 的 `loginPath` | `/z-dms/identity` | 无身份时跳转的身份选择页 |

## 六、已知限制

- **操作人身份不是真鉴权**：本地形态下 `dmsOperator` 由使用者自己填，审计流水里的「谁」**可以被随意填写**；
  生产靠 SSO / 网关注入 `X-Operator` 才可信。
- **`acquireConnection`（`POST /api/dms/sources/{id}/acquire`）已实现但没有任何页面调用**；
  审计流水里的「使用记录」实际来自后端在测连接时写入的流水。
- **变更历史 / 审计流水吞异常**：这两个页面的 `catch` 只 `setRows([])`，**界面不提示失败**，
  请求挂了会表现为「空列表」，容易误读成「没有记录」。
- 审计流水顶部的**类别（CHANGE / USAGE）过滤是前端过滤**（后端 `listAudit` 只按 `datasourceId` 查），
  类别筛选不减少后端返回的数据量。
- 所有列表都是**一次性全量拉取 + 前端分页**（`pageSize` 20），后端没有分页接口；数据源多了会拉全表。
- 数据源类型下拉是**前端写死的枚举**，后端新增类型不会自动出现在界面上。
- `credentialRef` 下拉的数据来自 `/api/dms/credential-refs`；该接口失败时下拉为空（其它已登记的引用会被并进选项）。
- **界面全链路无口令输入框**（凭据纪律）：口令由 z-mist 按引用取出，只在服务端内存里使用。
- vite `build.rollupOptions` **故意留空**：手工分 chunk 会造出循环 chunk，浏览器端直接白屏。

## 七、变更记录

| 版本 | 变更 |
|---|---|
| 1.0.0（未发布） | 新增组件文档页 `ComponentDocPage`（lead 008 §7）：`src/DOC.md` + 基座 `ComponentDoc` 薄包装 + 菜单末项 |
| 1.0.0 | 首版：数据源登记台账 / 变更历史 / 审计流水 / 身份选择页面集 + manifest + `X-Operator` 拦截器 |
