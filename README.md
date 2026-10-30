# z-dms

> **数据源管控底座**（DMS = DataSource Management System）—— 统一登记组织里「各个应用要连的那些库」，
> 凭据只存引用、连接统一建池、连通性统一探活、变更统一推送。

---

## 📋 基本信息

| 字段 | 值 |
|------|-----|
| **仓库** | `z-dms`（数据源管控底座） |
| **Maven 坐标** | `io.github.yuku123:z-dms:${revision}` |
| **当前版本** | `1.0.0`（根 POM `<revision>`，CI-friendly versions + flatten-maven-plugin `oss` 模式） |
| **父项目** | `io.github.yuku123:z-boot-parent:1.2.0`（`<relativePath/>` 留空，parent 在 repo1 不在磁盘） |
| **运行口径** | Java 8 · Spring Boot 2.7.18 |
| **包名** | `com.zifang.z.dms.*`（与 `com.zifang.z.lc.report.*` / `com.zifang.z.etl.*` 同格） |
| **GitHub** | ⚠️ **远端仓待人工创建**（见文末「人工执行清单」；本仓目前是本地仓，无 remote） |
| **建仓卡** | `TASK-20261012-011`（lead 仓 `002_项目需求/002_任务队列/`） |

---

## 🎯 它解决什么

组织里「数据源」这件事本来有三套事实并存（`TASK-20261012-009` 盘点结论）：

1. **z-lc report 子域 SPI** —— 报表用的数据源，明文口令躺在 `properties` 泛化键值袋里；
2. **z-boot-datasource-starter** —— 应用**自身**的库（`z.base.db.{module}.*`）；
3. **各仓 yml 散配** —— `z.base.db.*` 散在 13 个仓、19 个模块名、12 种叶子键名里。

> ### 一句话边界
>
> **应用自身库 ≠ 共享业务数据源。**
>
> `z-boot-datasource-starter` 管「应用自己那份库」（自己的池、自己的事务、自己的 Mapper）；
> **`z-dms` 管「多个应用共享的那批业务数据源」**（身份、类型、凭据引用、跨应用编排）。
>
> 判据：只服务于单个应用 ⇒ 归 starter；要被多个应用/报表/ETL 任务按 id 引用 ⇒ 归 z-dms。
> 两者不互斥：z-dms 建池时**只复用 starter 的「建池」这一层**。

z-dms 就是给第 1、3 套收口的底座 —— **一个 core 两种暴露**：

- **形态 A（嵌入式 starter）**：宿主一行依赖引入即用；
- **形态 B（组织级 web 管控）**：admin + `_frontend`，集中登记/测连接/授权/审计/变更历史
  —— **已交付（`TASK-20261012-012`，见下文「形态 B」）**。

> 两种形态**共用同一份 `z-dms-core`**（不是两份代码）：形态 A 的宿主与形态 B 的管控台
> 建池、探活、换绑、落库走的都是 core 里那套 `DataSourceService`。

---

## 🏗️ 项目结构

```
z-dms/
├── pom.xml                          # 根聚合 POM：z-boot-parent:1.2.0，${revision}，常开 flatten(oss)
├── LICENSE                          # Apache-2.0
├── _doc/
│   ├── 001_arch/00-overview.md      # 架构总览
│   └── 002_deploy/init/             # 初始化 DDL（幂等脚本，按 V1→V4 顺序执行）
│       ├── V1__z_dms_datasource_def.sql
│       ├── V2__z_dms_datasource_change_log.sql
│       ├── V3__z_dms_datasource_change_log_values.sql   # 变更流水补「前后值」两列（幂等 ALTER）
│       └── V4__z_dms_datasource_usage_log.sql            # 使用流水（审计面的另一半）
├── z-dms-core/                      # 核心：模型 + 存储表 + 连接工厂 + 探活 + 变更事件（纯 Java 8）
├── z-dms-spring-boot-starter/       # 形态 A：自动装配（宿主一行依赖引入即用）
├── z-dms-demo-host/                 # 宿主样例（证明「一行依赖引入宿主」这件事本身可跑）
├── z-dms-admin/                     # 形态 B：组织级 web 管控宿主（登记/测连接/变更历史/审计流水）
└── _frontend/
    └── z-dms-suit/                  # 形态 B 控制台 SPA（React 19 + antd 6 + vite 6 + @yuku123/z-frontend-common）
```

### `z-dms-core` 的包面

| 包 | 内容 |
|---|---|
| `model` | `DataSourceDef` / `DataSourceType` / `Credential` / `DataSourceHealth` / `DataSourceProbeResult` / `DataSourceHealthReport` |
| `support` | `DataSourceDefValidator`（写入口闸）/ `SensitivePropertyKeys`（敏感键名清单）/ `DataSourceDefJson` |
| `credential` | `CredentialResolver`（SPI）/ `CredentialResolveException` |
| `pool` | `DataSourcePool` / `DataSourcePoolFactory` / `DruidDataSourcePool` / `DefaultDataSourcePoolFactory` |
| `registry` | `DataSourceRegistry`（换绑关旧池） |
| `health` | `DataSourceHealthProber`（带超时的真探活） |
| `store` | `DataSourceDefStore` / `JdbcDataSourceDefStore`（MySQL 落库） |
| `event` | `DataSourceChangeEvent` / `DataSourceChangeListener` / `DataSourceChangePublisher` |
| `cache` | `DataSourceDefinitionCache`（本地定义缓存） |
| `service` | `DataSourceService`（登记链：校验 → 建池 → 探活 → 换绑 → 缓存 → 落库 → 广播） |

---

## 🔐 凭据纪律（本仓的第一条硬规矩）

> **`DataSourceDef` 从第一天起只存 z-mist 引用（`credentialRef`），永不存明文。**
> （组织 008 凭证规范的延续；`TASK-20261012-009` 盘点 §4 的落点）

| 规则 | 落点 |
|---|---|
| ① 写入口零明文 | `DataSourceDefValidator` 拦敏感键（30 余种形态：`password`/`secret`/`token`/`api-key`/`access_token`…），**直接报错，不静默丢弃**；错误消息只列键名，不带值 |
| ② 读出口只解析引用 | `CredentialResolver` 按 `credentialRef` 取凭据，明文只在内存里拼进连接参数，**不写回定义** |
| ③ 明文只允许存在于非持久化内存态与凭据库本身 | 表结构里**没有口令列**（见 `_doc/002_deploy/init/`）；`Credential#toString` 对口令打码 |

`credentialRef` 形态：`<group>/<key>` —— 即 **z-mist 的键名**，例如 `dms-demo/mysql-main`。

**两种解析器**（starter 自动装配）：

| 实现 | 何时用 | 说明 |
|---|---|---|
| `MistCredentialResolver` | `z.dms.mist.enabled=true` | 走 z-mist 客户端长连接协议取密文（**形态 A 正路**）；密文内容形如 `{"username":"...","password":"..."}` |
| `EnvironmentCredentialResolver` | 默认（未开 z-mist） | 从 Spring Environment 取，值由环境变量 / 外部配置注入；**仅供离线自检与无 z-mist 的宿主** |

---

## 🔌 宿主怎么用（形态 A）

**一行依赖**：

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-dms-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**拿服务**：

```java
@Autowired
private DataSourceService dataSourceService;

// 登记一个数据源（注册即探活，结论随结果返回）
Map<String, String> props = new HashMap<>();
props.put("host", "10.0.0.9");
props.put("port", "3306");
props.put("database", "oc");
DataSourceDef def = new DataSourceDef("ds-main", "主库", DataSourceType.JDBC, "dms-demo/mysql-main", props);
RegistrationResult result = dataSourceService.register(def);
if (!result.isUp()) {
    throw new IllegalStateException("数据源连不通: " + result.getProbe().getDetail());
}

// 宿主内取连接执行查询
try (Connection conn = dataSourceService.require("ds-main").getConnection()) {
    // ...
}
```

**配置面**：

| 键 | 含义 |
|---|---|
| `z.base.db.dms.*` | 元数据库（数据源定义的存储）连接，形状沿用 z-boot 的 `z.base.db.{module}.*` 约定 |
| `z.dms.enabled` | 总开关（默认开） |
| `z.dms.store-enabled` | 是否落库（默认关；不配元数据库也能纯内存用） |
| `z.dms.probe-interval-ms` / `z.dms.probe-timeout-ms` | 定时探活间隔 / 单池探活超时 |
| `z.dms.health-path` | 健康端点路径（默认 `/dms/health`） |
| `z.dms.mist.*` | z-mist 连接与分组（`enabled` / `host` / `port` / `app-name` / `app-secret` / `namespace` / `group`） |
| `z.dms.config.*` | z-config 推送通道（`enabled` / `publish` / `server-addr` / `namespace` / `group` / `data-id`） |

---

## 🩺 探活与健康面（失败不静默）

| 能力 | 落点 |
|---|---|
| **注册即探活** | `DataSourceService#register` 建池后立刻真借一次连接跑 `SELECT 1`，结论随 `RegistrationResult` 返回 |
| **定时探活** | `DataSourceHealthScheduler`（独立调度线程，不要求宿主开 `@EnableScheduling`），失败打 **ERROR** 日志 |
| **宿主可见** | `GET /dms/health`（starter 自带）与 actuator 健康贡献（有池 DOWN ⇒ `/actuator/health` 回 **503**） |
| **超时隔离** | 探活跑在独立线程 + 超时：池 `maxWait` 可能是 -1（无限等），在请求线程里借连接会让健康检查自己挂死 |
| **根因可读** | 池里借不到连接时再**直连一次**取根因（`ConnectException: Connection refused` / 认证失败），而不是只报一句 `timed out` |

> 口径抄 z-lc `DataSourceHealthProber`：**「至少一个池且全部 UP」才算 UP**。
> 理由（z-lc 源码注释原话）：部署件在没有任何可用连接时照样回 200 `UP`，
> 健康检查是部署闸唯一的判据，它说 UP 而库连不上，闸就变成谎话的放大器。

---

## 🏊 连接工厂（对齐 z-boot 约定）

- 池实现：`com.alibaba.druid.pool.DruidDataSource`（与 `z-boot` `ModuleDataSourceTemplate` 同款）；
- URL 拼装：`jdbc:mysql://{host}:{port}/{database}?serverTimezone=UTC&useUnicode=true&characterEncoding=utf-8`（`ModuleDataSourceTemplate:24` 逐字对齐），显式 `jdbc-url` 优先；
- 池参数键名沿用 z-boot：`initial-size` / `min-idle` / `max-active` / `max-wait` / `remove-abandoned` / `remove-abandoned-timeout`；
- 保活口径：`testWhileIdle=true` + `validationQuery=SELECT 1` + `keepAlive=true` + `timeBetweenEvictionRunsMillis=30000` + `minEvictableIdleTimeMillis=60000`；
- **两道护栏**（学 z-lc `LcModuleDataSource`）：
  1. 拿不到连接串 / 库名 ⇒ **拒绝建池**（不拼「能起来但一条连接都用不了」的默认串）；
  2. 驱动与 URL 不配套 ⇒ **拒绝建池**（那种组合实测 8 分钟 13 GB 日志）。

> **一处刻意偏差**：z-boot 是「应用自身库」，用 `initial-size=5 / min-idle=5` 保证进程一起来就有连接；
> z-dms 管的是**外部业务数据源**，那个库可能本来就连不通。`min-idle>0` 会让 Druid 建连线程在后台
> **无退避重试**（z-lc 实测 8 分钟 13 GB 日志）。故 z-dms 默认 `initial-size=0 / min-idle=0`：
> 连接按需创建，失败只影响那一次探活，不产生后台日志风暴。键名不变，可逐个数据源覆盖。

**换绑旧池显式关闭**：同 id 重新登记时旧池被 `close()`（行为沿用 z-lc report 子域 `DataSourceRegistry` 已验证的做法 —— 不关的话每重复登记一次就泄漏一个池）。

---

## 🔁 变更推送（z-config 长连接）

- **本地缓存**：定义在进程内有一份（`DataSourceDefinitionCache`），取数路径是「查内存 → 拿池」，不依赖元数据库可用性；
- **推送**：`z.dms.config.enabled=true` ⇒ starter 用 z-config 客户端 `addListener(dataId, group, …)` 订阅；
  推来的内容解析成定义全量 ⇒ 应用（有的换绑、远端没有的本地摘除）；
- **发布**：`z.dms.config.publish=true` ⇒ 本进程的定义变更会把全量定义 JSON 回推 z-config（`saveConfig`）；
- **推送内容里只有 `credentialRef`，没有明文口令。**

---

## 🗄️ 存储（MySQL，键 `z.base.db.dms.*`）

| 表 | 用途 |
|---|---|
| `z_dms_datasource_def` | 数据源定义（id / name / type / credential_ref / properties(JSON) / created_at / updated_at） |
| `z_dms_datasource_change_log` | 变更流水（形态 B 审计面 · V3 补了 `before_value` / `after_value` 两个前后值快照列） |
| `z_dms_datasource_usage_log` | 使用流水（形态 B 审计面 · 谁在什么时候用了哪个数据源） |

DDL 在 `_doc/002_deploy/init/`，**幂等**（`CREATE TABLE IF NOT EXISTS`；V3 对旧表用
`information_schema` 判列存在再 `PREPARE` 动态 ALTER）：执行两遍无报错。

---

## 🖥️ 形态 B（组织级 web 管控 · `z-dms-admin` + `_frontend/z-dms-suit`）

> 「组织级」的意思是：**一屏看清全组织登记了哪些数据源、每个健康态如何、谁改过、谁用过**。
> 它与形态 A 共用 `z-dms-core`（**不是两份代码**）：建池 / 探活 / 换绑 / 落库都走 core 的
> `DataSourceService`，管控台只提供「管控面」。

### 启动与构建

```bash
# 后端（形态 B 宿主）
cd z-dms-admin && mvn -o clean install
java -jar z-dms-admin/target/z-dms-admin-1.0.0-exec.jar
#   端口 z.dms.admin / ZDMS_ADMIN_PORT（默认 18098）

# 前端（控制台 SPA）
cd _frontend/z-dms-suit && npm install && npm run build
#   产物 dist/ 由 z-dms-admin 的 maven-resources-plugin 拷进 jar 的 BOOT-INF/classes/static/
#   ⇒ java -jar 单容器即带控制台（Mode 1 合体）
```

### 登记字段口径

| 字段 | 含义 | 约束 |
|---|---|---|
| `id` | 数据源稳定标识 | 必填；建后不可改；删除后不复用 |
| `name` | 展示名 | 必填 |
| `type` | `JDBC` / `MEMORY` / `CSV` / `HTTP` / `SQL` | **当前只有 JDBC 的连接工厂落地**，其余类型在界面上置灰 |
| `credentialRef` | 凭据引用（`<group>/<key>`，指向 z-mist 键名） | JDBC 必填；**界面上只有这一个下拉选择器，没有口令输入框** |
| `properties` | 非敏感连接属性（host / port / database / 池参数…） | 出现敏感键 ⇒ **写入口直接拒绝**（`DataSourceDefValidator`，不静默丢弃，报错只列键名不带值） |

### 测连接语义（**真发起连接，不是回固定值**）

| 端点 | 语义 |
|---|---|
| `POST /api/dms/sources/{id}/test-connection` | 走已登记的池借一次连接跑 `SELECT 1`；回执带 `up` / `detail` / `latencyMs` / `target` |
| `POST /api/dms/sources/test-connection` | 登记表单里的「自测」：现场建一个**临时池** → 真探一次 → 立刻关掉（不登记进注册中心） |
| `POST /api/dms/sources/{id}/acquire` | 真取一次连接跑一条 `SELECT`，并写一条**使用流水** |

失败时 `detail` 带**根因**（`ConnectException: Connection refused` / `Access denied` …），
不是一句「失败」；列表页的健康态是**当场探出来的**，不是缓存里的旧结论。

### 审计流水范围（**变更与使用都留痕**）

| 类别 | 表 | 内容 |
|---|---|---|
| 变更 `CHANGE` | `z_dms_datasource_change_log` | 谁、何时、把哪个源**从什么改成了什么**（`before_value` / `after_value` 两个定义快照 JSON；新增时前值为空，删除时后值为空） |
| 使用 `USAGE` | `z_dms_datasource_usage_log` | 谁、何时、**用了哪个数据源**、动作（测连接 / 取连接）、成功与否、耗时、结论 |

- 操作人取自请求头 `X-Operator`（生产由平台 SSO / 网关下发；缺失时记 `anonymous`，
  **不猜、不编**）；
- 两类流水在界面上合成一条时间轴（`GET /api/dms/audit`），变更历史单独看
  （`GET /api/dms/changes`）；
- 快照里**只有 credentialRef 与非敏感属性**，敏感键一律打码成 `***`（纵深防御）。

### API 一览

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/dms/sources` | 一屏：全部登记数据源 + 各自健康态 |
| POST | `/api/dms/sources` | 登记（注册即探活） |
| PUT | `/api/dms/sources/{id}` | 修改（换绑：旧池显式关闭，新池重新探活） |
| DELETE | `/api/dms/sources/{id}` | 摘除（未登记 ⇒ 报错，不静默成功） |
| POST | `/api/dms/sources/{id}/test-connection` | 测连接（真发起） |
| POST | `/api/dms/sources/test-connection` | 测连接（未登记的候选） |
| POST | `/api/dms/sources/{id}/acquire` | 取连接（产生使用流水） |
| GET | `/api/dms/changes` | 变更历史（含前后值快照） |
| GET | `/api/dms/audit` | 审计流水（变更 + 使用） |
| GET | `/api/dms/credential-refs` | 凭据引用目录（界面下拉候选；**只有引用名，没有口令**） |

返回体统一 `{code, data, message}`（`code=200` 成功）。

### 凭据纪律在界面上的落点

- **界面全链路没有口令输入框**：登记 / 编辑 / 自测 / 身份页，渲染后 `input[type=password]`
  恒为 0（源码扫描 + DOM 级渲染复验双重证据，见 `TASK-20261012-012` 证据）；
- 凭据面只有 `credentialRef` 下拉选择器；口令由 z-mist 按引用取出，只在服务端内存里
  拼进连接参数；
- 仓内守卫（会红）：`z-dms-admin` 的 `DataSourceFormNoPasswordTest`（入参 DTO 无口令字段）、
  `NoPlaintextPasswordInputTest`（前端源码无口令输入框）、`ChangeSnapshotTest`（快照打码）。

---

## 🧾 本轮验收（`TASK-20261012-011` · 形态 A）

| 验收项 | 结论 | 证据 |
|---|---|---|
| 1. 一行依赖引入宿主 → 登记真实 MySQL → 测连接通 → 宿主内取连接执行查询 | ✅ 达成 | lead 仓 `evidence/TASK-20261012-011/acceptance-1-register-and-query.log`（真库 MySQL 8.0.46，查询返回 3 行真数据） |
| 2. 探活失败在宿主 `/health` 或日志可见（不静默） | ✅ 达成 | `acceptance-2-probe-failure-visible.log`（故意连不通的源：`/health` DOWN + actuator 503 + ERROR 日志 + 直连根因） |
| 3. 全链路 grep 零明文口令；`credentialRef` 指向 z-mist 键名 | ✅ 达成 | `acceptance-3-no-plaintext-credential.log`（扫描器零命中；口令字面量逐字节搜 0 处；表里无口令列） |
| 4. DDL 在 `oc` 库执行两遍无报错 | ✅ 达成 | `ddl-twice-on-oc.log`（真 MySQL 8.0.46，四个脚本两遍各 `exit=0`） |

## 🧾 本轮验收（`TASK-20261012-012` · 形态 B）

| 验收项 | 结论 | 证据 |
|---|---|---|
| 1. 一屏看到全部登记数据源与各自健康态；测连接按钮真实发起连接 | ✅ 达成 | lead 仓 `evidence/TASK-20261012-012/acceptance-run.log`（三个源逐行健康态 UP/DOWN/UP；测连接回执带真实耗时与根因） |
| 2. 变更历史可回看每一次修改的前后值；审计流水含使用记录 | ✅ 达成 | 同上（`before_value`/`after_value` 快照对照 + `USAGE` 使用流水含操作人/耗时） |
| 3. 界面全链路无明文口令输入框（只有 credentialRef 选择器） | ✅ 达成 | `scan-no-plaintext-password.log`（源码扫描 0 命中）+ `dom-no-password-input.log`（6 个渲染面 `input[type=password]` 合计 0） |
| 4. `_frontend` 生产构建通过 | ✅ 达成 | `frontend-build.log`（`tsc --noEmit && vite build` ⇒ `✓ built in 1.98s`） |

---

## ⚠️ 人工执行清单（本仓当前**没有远端**，以下需人工）

```bash
# 1) 建远端仓 + 定可见性（对外动作，不擅自做；可见性由人工定）
gh repo create z-opc-foundation/z-dms --private --description "数据源管控底座：DataSourceDef + credentialRef + Druid 连接工厂 + 定时探活 + z-config 推送"
#    判据：gh repo view z-opc-foundation/z-dms → 返回仓库信息；git ls-remote 能连上

# 2) 推本仓（本机已 commit，无 remote）
cd /Users/zifang/workplace/ceo_workplace/z-opc-foundation/z-dms
git remote add origin git@github.com:z-opc-foundation/z-dms.git
git push -u origin main
#    判据：git ls-remote origin 列出 main 的 commit id，且与本地 HEAD 一致

# 3) 如需发 Maven Central（-Pcentral profile 休眠中）
mvn -Pcentral deploy
#    判据：central.sonatype.com 上能看到 z-dms / -core / -spring-boot-starter 的发布记录
#    ⚠️ 本轮**不发**：发中央属对外动作，需人工授权
#    注：z-dms-admin 是 standalone 宿主（不上中央），与 z-ctc-admin 同款口径
#        （maven.deploy.skip=true / exec.jar classifier）

# 4) 接真实 z-mist（生产口径的凭据解析）
#    宿主配置：z.dms.mist.enabled=true + z.dms.mist.host/port/app-name/app-secret/namespace
#    判据：在 z-mist 上建键 <group>/<key>，密文内容形如 {"username":"...","password":"..."}；
#          宿主登记同 credentialRef 的数据源 ⇒ 探活 UP 且日志里看不到任何口令
```

---

## 文档目录

本项目文档统一收口在 `_doc/` 下（八类固定编号表，桶按需存在）:

- [`_doc/001_arch/`](_doc/001_arch/) — 架构总览
- [`_doc/002_deploy/`](_doc/002_deploy/) — 部署运维与初始化 DDL（`init/`）
- [`_doc/003_script/`](_doc/003_script/) — 构建与发布脚本

## 📚 相关卡与文档

- `TASK-20261012-009` —— 数据源三套事实现读盘点（本仓的口径来源，lead 仓 `002_项目需求/001_特性需求/008_数据集成_z-dms_与_z-etl/notes.md`）
- `TASK-20261012-012` —— 形态 B 组织级 web 管控（**本仓已交付**：`z-dms-admin` + `_frontend/z-dms-suit`）
- `TASK-20261012-013` —— z-etl 仓骨架（z-dms 的第一个重度消费者）
- 需求 008 —— 数据集成域总纲（lead 仓 `002_项目需求/001_特性需求/008_数据集成_z-dms_与_z-etl/`）
