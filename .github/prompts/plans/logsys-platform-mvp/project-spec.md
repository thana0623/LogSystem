> plan: logsys-platform-mvp
> phase: project-spec
> status: confirmed
> base: focus-spec.md

# 项目技术规范

## 1. 技术栈与版本

### 基础设施

| 组件 | 版本 | 用途 | 镜像 |
|------|------|------|------|
| Docker Engine | 24.0+ | 容器运行时 | — |
| Docker Compose | v2.20+ | 服务编排 | — |
| ClickHouse | 24.8 | 日志主存储 | `clickhouse/clickhouse-server:24.8` |
| PostgreSQL | 16.4 | 元数据存储 | `postgres:16.4-alpine` |
| Vector | 0.40 | 日志采集/ETL | `timberio/vector:0.40.0-alpine` |
| Grafana | 11.2 OSS | 预置仪表盘 | `grafana/grafana-oss:11.2.0` |

### 后端

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 (LTS) | 最低 Java 版本 |
| Spring Boot | 3.3.x | 主框架 |
| Spring Web | 6.1.x (随 Boot) | REST API |
| MyBatis | 3.0.x | ClickHouse + PostgreSQL 双数据源 |
| ClickHouse JDBC | 0.6.x | ClickHouse 驱动 |
| PostgreSQL JDBC | 42.7.x | PostgreSQL 驱动 |
| Flyway | 10.x | PostgreSQL 迁移 |
| Lombok | 1.18.x | POJO 简化 |
| Jackson | 2.17.x (随 Boot) | JSON 序列化 |
| SLF4J + Logback | 随 Boot | 自身日志 |
| Maven | 3.9.x | 构建工具 |

### Python 分析模块（Phase 2）

| 组件 | 版本 | 说明 |
|------|------|------|
| Python | 3.12 | 最低 Python 版本 |
| clickhouse-driver | 0.2.x | ClickHouse 客户端 |
| psycopg2 | 2.9.x | PostgreSQL 客户端 |
| schedule | 1.2.x | 定时任务调度 |
| python-dotenv | 1.0.x | 环境变量加载 |

### 前端（Phase 2 — 专业前端负责）

| 组件 | 版本 | 说明 |
|------|------|------|
| Next.js | 14.x | React 全栈框架（内置 React 18 + App Router） |
| TypeScript | 5.5+ | 类型系统，strict 模式 |
| TailwindCSS | 3.4 | 原子化样式 |
| shadcn/ui | latest | 组件基座（仅结构） |
| Zustand | 4.5 | 客户端状态 |
| TanStack Query | 5.x | 服务端状态 |
| Recharts | 2.12 | 图表 |
| Framer Motion | 11.x | 微动效 |
| Lucide Icons | 0.400+ | 图标 |
| MSW | 2.x | Mock Service Worker |

---

## 2. 项目目录结构

```
logsys/                              # Monorepo 根
├── docker-compose.yml               # 一线部署文件
├── .env.example                     # 环境变量模板
├── .gitignore
├── Makefile                         # make up / make down / make logs / make reset
├── README.md
├── LICENSE                          # Apache 2.0
│
├── docs/                            # 对外文档
│   ├── architecture.md
│   ├── quickstart.md
│   └── log-format-spec.md           # 日志 JSON Schema 规范（给业务方看）
│
├── deploy/                          # 部署相关
│   ├── vector/
│   │   ├── vector.toml              # Vector 采集配置
│   │   └── pipelines/               # 按日志来源拆分配置（可选）
│   │       ├── docker-stdout.toml
│   │       └── file-logs.toml
│   ├── clickhouse/
│   │   ├── init.sql                 # 建表 + 索引（schema.sql 的副本）
│   │   └── config.xml               # ClickHouse 服务端配置覆盖
│   ├── postgres/
│   │   └── init.sql                 # services 表 + 触发器
│   └── grafana/
│       ├── dashboards/
│       │   ├── overview.json
│       │   └── errors.json
│       └── datasources/
│           └── clickhouse.yml
│
├── logsys-api/                      # Spring Boot 后端
│   ├── pom.xml
│   ├── Dockerfile
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/logsys/
│   │   │   │   ├── LogsysApplication.java
│   │   │   │   ├── config/          # 数据源、CORS、Jackson 配置
│   │   │   │   │   ├── ClickHouseConfig.java
│   │   │   │   │   ├── PostgresConfig.java
│   │   │   │   │   └── WebConfig.java
│   │   │   │   ├── controller/      # REST Controller（薄层，不含业务逻辑）
│   │   │   │   │   ├── HealthController.java
│   │   │   │   │   ├── LogQueryController.java
│   │   │   │   │   ├── ServiceController.java
│   │   │   │   │   ├── ErrorController.java
│   │   │   │   │   └── StatsController.java
│   │   │   │   ├── service/         # 业务逻辑层
│   │   │   │   │   ├── LogQueryService.java
│   │   │   │   │   ├── ServiceManageService.java
│   │   │   │   │   ├── ErrorClusterService.java
│   │   │   │   │   └── StatsService.java
│   │   │   │   ├── dao/             # 数据访问层
│   │   │   │   │   ├── clickhouse/  # ClickHouse Mapper
│   │   │   │   │   │   ├── LogMapper.java
│   │   │   │   │   │   └── ErrorClusterMapper.java
│   │   │   │   │   └── postgres/    # PostgreSQL Mapper
│   │   │   │   │       └── ServiceMapper.java
│   │   │   │   ├── model/           # DTO / VO / Domain
│   │   │   │   │   ├── dto/         # 请求体 DTO
│   │   │   │   │   │   ├── LogQueryRequest.java
│   │   │   │   │   │   └── ServiceCreateRequest.java
│   │   │   │   │   ├── vo/          # 响应体 VO
│   │   │   │   │   │   ├── LogEntryVO.java
│   │   │   │   │   │   ├── ErrorClusterVO.java
│   │   │   │   │   │   └── PageResult.java
│   │   │   │   │   └── entity/      # 数据库实体（对应表结构）
│   │   │   │   │       └── ServiceEntity.java
│   │   │   │   ├── common/          # 通用工具
│   │   │   │   │   ├── ApiResponse.java     # 统一响应封装
│   │   │   │   │   ├── ErrorCode.java       # 错误码枚举
│   │   │   │   │   ├── PageResult.java      # 分页结果封装
│   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   └── interceptor/     # 拦截器
│   │   │   │       └── RequestIdInterceptor.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-dev.yml
│   │   │       └── db/migration/    # Flyway 迁移脚本（PostgreSQL）
│   │   │           └── V1__init_services.sql
│   │   └── test/
│   │       └── java/com/logsys/
│   │           ├── controller/      # MockMvc 集成测试
│   │           │   ├── LogQueryControllerTest.java
│   │           │   └── ServiceControllerTest.java
│   │           └── service/         # Service 层单元测试
│
├── logsys-analyzer/                 # Python 分析模块（Phase 2）
│   ├── pyproject.toml
│   ├── Dockerfile
│   ├── main.py
│   ├── cluster/
│   │   ├── __init__.py
│   │   ├── signature.py
│   │   └── normalizer.py
│   └── stats/
│       ├── __init__.py
│       └── aggregator.py
│
├── logsys-ui/                       # 前端（Phase 2 — 专业前端负责）
│   ├── package.json
│   ├── next.config.js
│   ├── tailwind.config.ts
│   ├── tsconfig.json
│   ├── public/
│   └── src/
│       ├── app/                      # Next.js App Router（文件系统路由）
│       │   ├── layout.tsx
│       │   ├── providers.tsx
│       │   ├── dashboard/page.tsx
│       │   ├── logs/page.tsx
│       │   ├── errors/page.tsx
│       │   └── services/[name]/page.tsx
│       ├── components/
│       │   ├── ui/
│       │   └── layout/
│       ├── features/
│       ├── entities/
│       ├── shared/
│       │   ├── lib/
│       │   │   ├── api-client.ts       ← 唯一 API 调用入口
│       │   │   └── formatters.ts
│       │   └── styles/
│       │       ├── globals.css
│       │       └── tokens.css
│       └── mocks/
│           ├── server.ts
│           ├── handlers/
│           └── fixtures/
│
└── test/                             # 集成测试
    ├── docker-compose.test.yml
    └── smoke-test.sh
```

---

## 3. 命名规范

### 3.1 Java 包名

```
com.logsys.{module}.{layer}
```

| 层 | 包 | 示例 |
|----|-----|------|
| Controller | `com.logsys.controller` | `LogQueryController` |
| Service | `com.logsys.service` | `LogQueryService` |
| DAO (ClickHouse) | `com.logsys.dao.clickhouse` | `LogMapper` |
| DAO (PostgreSQL) | `com.logsys.dao.postgres` | `ServiceMapper` |
| DTO | `com.logsys.model.dto` | `LogQueryRequest` |
| VO | `com.logsys.model.vo` | `LogEntryVO` |
| Entity | `com.logsys.model.entity` | `ServiceEntity` |
| Config | `com.logsys.config` | `ClickHouseConfig` |
| Common | `com.logsys.common` | `ApiResponse` |

### 3.2 Java 类命名

- **Controller**：`{Entity}Controller` — 如 `LogQueryController`、`ServiceController`
- **Service**：`{Entity}{Action}Service` — 如 `LogQueryService`
- **Mapper**：`{Entity}Mapper` — 如 `LogMapper`、`ServiceMapper`
- **DTO**：`{Entity}{Action}Request` — 如 `LogQueryRequest`
- **VO**：`{Entity}VO` — 如 `LogEntryVO`
- **Entity**：`{TableName}Entity` — 如 `ServiceEntity`

### 3.3 ClickHouse 命名

- **表名**：小写 + 下划线，如 `logs`、`error_clusters`
- **列名**：小写 + 下划线，如 `service_name`、`exception_type`
- **索引名**：`idx_{列名}` 如 `idx_message`
- **物化视图**：`mv_{描述}`

### 3.4 PostgreSQL 命名

- **表名**：小写 + 下划线，复数，如 `services`
- **列名**：小写 + 下划线，如 `service_name`
- **约束**：`chk_{表}_{列}` 如 `chk_services_name`
- **触发器**：`trg_{表}_{动作}` 如 `trg_services_updated_at`
- **Flyway 迁移**：`V{序号}__{描述}.sql` 如 `V1__init_services.sql`

### 3.5 API 路径命名

- **全部小写**，多词用连字符：`/error-clusters` ❌ → `/errors/clusters` ✅
- **资源用复数**：`/service` ❌ → `/services` ✅
- **版本前缀**：`/api/v1/`
- **动作用 HTTP 方法表达**：`POST /logs/query`（不用 `/logs/getLogs`）

### 3.6 Git 分支命名

```
feature/api-{描述}          ← 后端 API 开发
feature/ui-{描述}           ← 前端 UI 开发
feature/cluster-{描述}      ← Python 分析模块
fix/{描述}                  ← Bug 修复
docs/{描述}                 ← 文档变更
chore/{描述}                ← 构建/CI/杂务
```

### 3.7 前端命名

- **组件文件**：PascalCase — `LogTable.tsx`、`ErrorCard.tsx`
- **工具文件**：kebab-case — `api-client.ts`、`formatters.ts`
- **Mock 文件**：`{资源}.fixture.ts` — `logs.fixture.ts`
- **页面目录**：kebab-case — `log-explorer/`、`error-cluster/`
- **CSS 变量**：`--ls-{category}-{name}` — `--ls-color-error`, `--ls-font-mono`

---

## 4. 端口分配

| 服务 | 端口 | 协议 | 说明 |
|------|------|------|------|
| Spring Boot API | **8080** | HTTP | REST API |
| ClickHouse HTTP | **8123** | HTTP | JDBC + Vector 写入 |
| ClickHouse Native | **9000** | TCP | 原生协议（Python 驱动用） |
| PostgreSQL | **5432** | TCP | — |
| Grafana | **3000** | HTTP | 仪表盘 |
| 前端 Dev Server | **3000** | HTTP | Next.js 默认 |
| Vector | — | — | 不暴露端口，仅出站 |

**规则**：
- Docker Compose 内部网络：`logsys-net`（bridge），容器间用服务名通信
- 仅 8080 和 3000 映射到宿主机，其余仅内网访问

---

## 5. 环境变量清单

```bash
# ── ClickHouse ──
CLICKHOUSE_DB=logsys
CLICKHOUSE_USER=logsys
CLICKHOUSE_PASSWORD=changeme            # 部署时修改
CLICKHOUSE_MAX_MEMORY=536870912         # 512MB 限制，单位字节

# ── PostgreSQL ──
POSTGRES_DB=logsys_meta
POSTGRES_USER=logsys
POSTGRES_PASSWORD=changeme              # 部署时修改

# ── 后端 ──
LOG_LEVEL=INFO
SERVER_PORT=8080

# ── Grafana ──
GF_SECURITY_ADMIN_USER=admin
GF_SECURITY_ADMIN_PASSWORD=changeme     # 部署时修改
GF_INSTALL_PLUGINS=""                   # 不装插件，保持轻量

# ── 前端（Phase 2） ──
NEXT_PUBLIC_API_BASE=http://localhost:8080
NEXT_PUBLIC_ENABLE_MOCK=true            # 开发模式=true, 联调=false
```

---

## 6. Docker Compose 健康检查策略

```yaml
# 启动顺序（healthcheck 控制）
# 1. clickhouse  ← 最先启动，初始化需要 10-15s
# 2. postgres    ← 与 clickhouse 并行
# 3. vector      ← depends_on: clickhouse（等 ClickHouse 就绪）
# 4. logsys-api  ← depends_on: clickhouse, postgres
# 5. grafana     ← depends_on: clickhouse

# 健康检查命令：
# ClickHouse: wget -qO- http://localhost:8123/ping
# PostgreSQL: pg_isready -U logsys
# Spring Boot: wget -qO- http://localhost:8080/health
```

---

## 7. 代码规范要点

### Java

- Controller 只做三件事：**接收请求 → 调 Service → 返回响应**。禁止 Controller 里写 SQL、拼接字符串、处理异常
- Service 不碰 HttpServletRequest / HttpServletResponse，只处理 POJO
- Mapper 只写 SQL，不写业务逻辑
- DTO 与 VO 必须分离：DTO 是入参、VO 是出参，不允许同一个类同时用于入参和出参
- 禁止 `SELECT *`，ClickHouse 查询必须显式列出列名
- ClickHouse SQL 参数用 MyBatis `#{}`（预编译），排序字段用 `${}` 但必须校验白名单

### Python

- 每个脚本必须能重复执行（幂等）
- 数据库连接参数从环境变量读取
- 异常签名归一化函数必须有单元测试

### 前端

- 组件不包含 API 调用逻辑（统一走 `api-client.ts`）
- 组件不包含业务逻辑（统一走 `features/` 层）
- 禁止 `any` 类型
- 禁止 `console.log` 提交到仓库

---

## 8. CI / CD 策略

### Phase 1（GitHub Actions / Gitea Actions）

```yaml
# 触发条件：PR 到 develop 或 main
# 步骤：
# 1. Java 编译 + 单元测试（mvn test）
# 2. Docker 镜像构建（验证 Dockerfile 可构建）
# 3. ShellCheck（lint 所有 .sh 文件）

# Phase 2 追加：
# 4. 前端 TypeScript 编译检查（tsc --noEmit）
# 5. 前端 Lint（eslint）
# 6. Python 单元测试（pytest）
```

### 不包含（MVP 阶段不做）

- 自动化部署
- E2E 测试
- 性能基准测试
- 镜像推送到 Registry

---

## 9. 关键设计决策

| 决策 | 原因 |
|------|------|
| Monorepo 而非多仓库 | 项目规模小（< 100 文件），单仓库降低协作成本 |
| Java 17 而非 21 | LTS 覆盖广，实验室环境更常见 |
| Flyway 而非 Liquibase | Flyway 更轻量，SQL 直写，学习成本低 |
| DTO/VO 分离 | 防止 API 契约变更时连累数据库实体 |
| 前端 App Router + 模块化 | Next.js 文件系统路由 + components/features/entities/shared 分层 |
| 无认证/无 RBAC | 内网裸奔，聚焦日志能力 |
| ENV 注入而非配置文件挂载 | 单机部署，环境变量最简单，减少文件管理复杂度 |
