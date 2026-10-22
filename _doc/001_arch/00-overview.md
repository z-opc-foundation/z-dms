# z-dms 架构总览（形态 A）

> 一句话：**一个 core 两种暴露**。本仓当前只交付形态 A（嵌入式 starter）；
> 形态 B（组织级 web 管控）是 `TASK-20261012-012`。

---

## 一、组件关系

```
   宿主应用 (z-lc / z-etl / 任意 z-* 应用)
        │  一行依赖：io.github.yuku123:z-dms-spring-boot-starter
        ▼
┌──────────────────────────────────────────────────────────┐
│ z-dms-spring-boot-starter（形态 A 暴露）                  │
│  ├ DmsAutoConfiguration      自动装配（可选依赖全部走     │
│  │                            @ConditionalOnClass 嵌套类） │
│  ├ EnvironmentCredentialResolver / MistCredentialResolver │
│  ├ DmsHealthController       GET /dms/health             │
│  ├ DmsHealthIndicator        actuator 贡献（DOWN ⇒ 503） │
│  ├ DataSourceHealthScheduler 定时探活（独立线程）          │
│  └ ZConfigDataSourceChangeBridge  长连接推送（收 + 发）    │
└───────────────────────────┬──────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────┐
│ z-dms-core（纯 Java 8，无 Spring 容器面）                  │
│  DataSourceService ── 登记链                                │
│    ① DataSourceDefValidator   写入口闸（敏感键 ⇒ 报错）    │
│    ② DataSourcePoolFactory    建池（Druid）                │
│    ③ DataSourceHealthProber   注册即探活（带超时）          │
│    ④ DataSourceRegistry       换绑（显式关闭旧池）          │
│    ⑤ DataSourceDefinitionCache 本地缓存                   │
│    ⑥ DataSourceDefStore       落库（MySQL）               │
│    ⑦ DataSourceChangePublisher 广播（z-config）            │
└──────────────────────────────────────────────────────────┘
        │                    │                    │
        ▼                    ▼                    ▼
   z-mist（凭据）      MySQL（z.base.db.dms.*）   z-config（推送）
```

---

## 二、登记链（`DataSourceService#register`）

| 步 | 做什么 | 失败时 |
|---|---|---|
| ① 校验 | id/name/type 非空；`properties` 无敏感键；JDBC 必给 `credentialRef`（`<group>/<key>`） | 抛 `IllegalArgumentException`，**消息只列键名不带值** |
| ② 建池 | 解析凭据 → 拼 URL → 造 Druid 池；两道护栏（无连接串 / 驱动与 URL 不配套 ⇒ 拒绝建池） | 抛 `IllegalStateException`，消息说明为什么拒绝 |
| ③ 探活 | 真借一次连接跑 `SELECT 1`（独立线程 + 超时；失败再直连一次取根因） | **不抛**：`probe= DOWN`，打 ERROR 日志，继续登记（失败要看得见，不是拒绝登记） |
| ④ 换绑 | 同 id 已存在 ⇒ 旧池 `close()`，新池顶上 | 旧池关闭失败只 WARN，不影响换绑 |
| ⑤ 缓存 | 写进本地定义缓存 | — |
| ⑥ 落库 | `upsert` 到 `z_dms_datasource_def`（落库前再过一次写入口闸） | 抛异常（数据面问题必须暴露） |
| ⑦ 广播 | 进程内监听器 + `DataSourceChangePublisher`（z-config 回推） | 发布失败只 WARN（本地已生效） |

---

## 三、探活口径

| 维度 | 取值 | 依据 |
|---|---|---|
| 触发 | 登记时（注册即探活）+ 定时（`z.dms.probe-interval-ms`，默认 60s） | 卡面「注册即探活」「定时探活 → 失败上报」 |
| 判定 | 真借连接跑 `SELECT 1` | z-lc `DataSourceHealthProber` 同款 |
| 超时 | `z.dms.probe-timeout-ms`（默认 2000ms），独立线程 | 池 `maxWait` 可能是 -1（无限等），请求线程里借连接会让健康检查自己挂死 |
| 整体 | 「至少一个池且全部 UP」才 UP，否则 DOWN + 第一个失败原因 | z-lc 同款（健康闸不能对连不上的库说 UP） |
| 失败呈现 | ① `/health` 与 `/dms/health` 的 `sources[].detail` ② actuator 503 ③ ERROR 日志 ④ 直连根因 | 「不静默」 |

---

## 四、凭据面

```
credentialRef ("<group>/<key>")
        │
        ├─ MistCredentialResolver ──► z-mist 客户端（长连接协议）──► 密文 {"username":..,"password":..}
        │                                   （形态 A 正路）
        └─ EnvironmentCredentialResolver ──► Spring Environment（值由环境变量注入；离线自检用）
        │
        ▼
   Credential（内存态，toString 打码）
        │
        ▼
   DruidDataSource.username / .password   ← 明文只到这里为止，不回写定义、不落库、不进日志
```

**三条硬规则**（`TASK-20261012-009` §4.3）：
写入口零明文 / 读出口只解析引用 / 明文只允许存在于非持久化内存态与凭据库本身。

---

## 五、与邻居的分工

| 邻居 | 它管 | z-dms 怎么接 |
|---|---|---|
| `z-boot-datasource-starter` | 应用**自身**的库（`z.base.db.{module}.*` → Druid → MyBatis-Plus） | **不动它**；z-dms 只借用「Druid 建池」这一层的口径（URL 拼法、池参数键名、保活口径） |
| `z-lc` report 子域 | 报表数据源 SPI（`DataSourceDef` / `DataSourceRegistry` / `JdbcReportDataSource`） | **不改它**；本仓 `DataSourceDef` 沿用同名同字段并**新增 `credentialRef`**，让 z-lc 侧不需要为接 z-dms 改造；`DataSourceType` 沿用它的 5 值枚举 |
| `z-mist` | 凭据库 | 引用它（`credentialRef` → 键名 → 密文），不自建凭据面 |
| `z-config` | 配置中心（长连接推送） | 用它推定义变更；本地缓存因此不必轮询元数据库 |
| `z-etl` | ETL 引擎 | z-dms 的第一个重度消费者（`TASK-20261012-013`），本仓不写 ETL 代码 |

---

## 六、已知边界（诚实标注）

| 项 | 现状 |
|---|---|
| z-mist 真连 | 本轮**没有真连 z-mist 服务端**（不连生产）；`MistCredentialResolver` 以替身单测覆盖引用形态/缺失/异常不带值三条，真连留给人工项 |
| z-config 真连 | 本轮**没有真连 z-config 服务端**；桥接层按 z-config 客户端 API 写好并编译通过，真连留给人工项 |
| 远端仓 | **未创建**（对外动作，人工保留）；本仓当前是本地仓、无 remote |
| Maven Central | **未发**（`-Pcentral` profile 休眠中，发中央属对外动作，人工保留） |
| 非 JDBC 类型 | `MEMORY/CSV/HTTP/SQL` 四种类型模型已留位，连接工厂当前只落地 JDBC |
