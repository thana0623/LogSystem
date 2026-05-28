> task-id: logsys-platform-mvp
> created: 2026-05-24T20:30:00+08:00
> status: confirmed

## 1. 场景还原

**角色**：实验室学生 / 小团队开发者 / 独立开发者
**场景**：一台 2C4G Linux 服务器上跑了 3-5 个不同语言的后端服务（Java Spring Boot、Python Flask、Node.js Express），开发者需要统一查看所有服务的日志，快速定位错误，了解服务运行趋势。当前做法是 SSH 上去 tail/grep 各自目录的日志文件，效率极低，且无法跨服务关联。
**核心诉求**：一条 `docker compose up -d` 搞定部署，不再 SSH 翻文件，在一个界面里搜索、过滤、看趋势、定位 Top 错误。

---

## 2. 核心业务边界

### IN（范围内）

- 日志统一收集：支持 stdout、文件日志两种来源
- 多语言兼容：定义标准 JSON 日志格式 + 非标日志的 Vector 转换规则
- 日志结构化：非结构化日志经 Vector ETL 转结构化后入库
- 日志查询：按时间范围、服务名、日志级别、关键字组合查询
- 错误聚类：基于异常签名自动归类重复错误，输出 Top N
- 基础统计分析：日志量时序趋势、错误率、服务健康状态
- 可视化图表：Grafana 仪表盘 (Phase 1)，自研轻量前端 (Phase 2)
- AI 扩展预留：结构化数据 + 聚类结果作为 AI 输入，不分析原始日志
- Docker Compose 一键部署，单机运行，资源占用 < 1GB 内存

### OUT（范围内不做）

- 多节点集群 / 高可用 / 水平扩展
- 实时告警通知（钉钉/邮件/Webhook）— 留 Phase 2
- 多租户 / RBAC 权限 / 用户认证 — 内网裸奔，单用户
- 日志脱敏 / 合规审计
- 全链路追踪（只预留 trace_id 字段，不实现追踪系统）
- 日志回放 / 重放
- 与云厂商日志服务对接

---

## 3. 禁止触碰黑名单

- 禁止使用 ELK Stack（Elasticsearch + Logstash + Kibana）— 资源太重，不适合单机轻量场景
- 禁止对业务系统做代码侵入 — 不要求业务系统引入 SDK/Maven 依赖，通过标准输出 + 文件采集即可
- 禁止使用 Java Agent 字节码注入方式采集日志
- 禁止使用 Kafka / RabbitMQ 等消息队列作为日志缓冲 — 单机场景不需要
- 禁止在 MVP 阶段引入 Flink / Spark 流处理 — 过度设计
- 禁止使用物理删除 — ClickHouse 和 PostgreSQL 的数据保留通过 TTL / 分区过期实现

---

## 4. 核心测试断言清单

```
# 部署
assertDockerComposeUpSuccess()
assertEquals(5, runningContainers())  // vector, clickhouse, postgres, spring-boot, grafana

# 日志收集
assertClickHouseRowCount("logs", ">", 0)  // 模拟日志写入后能查到

# 结构化
assertColumnNotNull("service_name")
assertColumnNotNull("level")
assertColumnNotNull("timestamp")
assertColumnType("timestamp", "DateTime64(3)")

# 查询 API
assertHttpOk(POST /api/v1/logs/query, {service_name: "order-service", level: "ERROR", range: "1h"})
assertHttpOk(POST /api/v1/logs/query, {keyword: "NullPointerException"})
assertResponseJsonHasFields(["total", "items", "page", "pageSize"])

# 错误聚类
assertHttpOk(GET /api/v1/errors/top?range=24h&limit=10)
assertResponseFieldEquals("items[0].count", ">", 0)
assertResponseFieldEquals("items[0].signature", "not-null")

# 统计分析
assertHttpOk(GET /api/v1/stats/overview?range=24h)
assertResponseJsonHasFields(["totalLogs", "errorRate", "serviceCount", "logVolumeTrend"])

# Grafana
assertHttpOk(GET http://localhost:3000)  // Grafana 可访问
assertDashboardExists("logsys-overview")  // 预置仪表盘存在
```

---

# 系统架构设计

## 1. 总体架构

```
                            ┌─────────────────────────────────────────┐
                            │            Docker Network               │
                            │                                         │
  ┌──────────┐              │  ┌─────────┐    ┌──────────────┐        │
  │ Java App │── stdout ───▶│  │         │───▶│  ClickHouse  │        │
  └──────────┘              │  │         │    │  (日志存储)   │        │
  ┌──────────┐              │  │         │    └──────┬───────┘        │
  │Python App│── file ─────▶│  │ VECTOR  │           │               │
  └──────────┘              │  │(收集/ETL)│    ┌──────┴───────┐        │
  ┌──────────┐              │  │         │    │ PostgreSQL   │        │
  │Node App  │── stdout ───▶│  │         │    │ (元数据)     │        │
  └──────────┘              │  └─────────┘    └──────┬───────┘        │
                            │                       │               │
                            │                ┌──────┴───────┐        │
                            │                │ Spring Boot  │        │
                            │                │  (API + 查询) │        │
                            │                └──────┬───────┘        │
                            │                       │               │
                            │     ┌─────────────────┼───────┐        │
                            │     │                 │       │        │
                            │  ┌──┴──┐       ┌─────┴──┐ ┌──┴──────┐ │
                            │  │Grafana│      │Python  │ │自研前端  │ │
                            │  │(可视) │      │(分析)  │ │(Phase2) │ │
                            │  └─────┘       └────────┘ └─────────┘ │
                            │                                         │
                            └─────────────────────────────────────────┘
```

### 模块划分

| 模块 | 技术 | 职责 | 端口 |
|------|------|------|------|
| **vector** | Rust (官方镜像) | 日志采集、解析、结构化、转发 | — |
| **clickhouse** | ClickHouse 24.x | 日志主存储，列式查询 | 8123/9000 |
| **postgres** | PostgreSQL 16 | 元数据：服务注册、用户配置、分析结果缓存 | 5432 |
| **logsys-api** | Spring Boot 3.x | REST API、查询路由、服务管理 | 8080 |
| **logsys-analyzer** | Python 3.12 | 错误聚类、统计分析、定时任务 | — |
| **grafana** | Grafana OSS | 预置仪表盘、即开即用 | 3000 |
| **logsys-ui** | Next.js 14 + TypeScript + TailwindCSS + shadcn/ui + Zustand + TanStack Query + Recharts (Phase2) | 企业级查询与分析界面，专业前端负责开发 | 3001 |

---

## 2. 日志收集方案

### 2.1 推荐日志格式（标准 JSON Schema）

业务应用只需按以下 JSON 格式输出（一行一条），即可零配置接入：

```json
{
  "timestamp": "2026-05-24T12:00:00.123Z",
  "level": "ERROR",
  "logger": "com.example.OrderService",
  "message": "Failed to create order: insufficient stock",
  "service_name": "order-service",
  "service_instance": "order-service-7d8f9-abc12",
  "trace_id": "a1b2c3d4e5f67890",
  "span_id": "1234567890abcdef",
  "exception": {
    "type": "java.lang.IllegalStateException",
    "message": "insufficient stock for item 42",
    "stacktrace": "..."
  },
  "tags": {
    "order_id": "ORD-12345",
    "user_id": "U-678"
  },
  "fields": {
    "duration_ms": 1450,
    "db_query": "SELECT ..."
  }
}
```

### 2.2 多语言接入方式

| 语言 | 接入方式 | 侵入性 |
|------|----------|--------|
| **Java (Spring Boot)** | Logback JSON encoder → stdout → Docker json-file driver → Vector | 仅 logback.xml 配置，无代码依赖 |
| **Python** | python-json-logger → stdout/file → Vector | pip install 一个包 |
| **Node.js** | pino / winston JSON → stdout → Vector | npm install 一个包 |
| **Docker stdout** | docker-compose logging driver → Vector socket | 零侵入 |
| **裸文件日志** | Vector file source → multiline parser → JSON transform | 零侵入 |

### 2.3 Vector 配置思路

```toml
# vector.toml 核心结构

[sources.docker_stdout]        # 采集 Docker stdout
  type = "docker_logs"

[sources.file_logs]            # 采集挂载的文件日志
  type = "file"
  include = ["/logs/**/*.log"]

[transforms.structured]        # 已是 JSON → 直接透传
  type = "remap"
  inputs = ["docker_stdout"]
  source = '''
    . = parse_json!(.message) ?? .
    .service_name = .service_name ?? "unknown"
    .source_type = "stdout"
  '''

[transforms.unstructured]      # 非 JSON → 解析为结构化
  type = "remap"
  inputs = ["file_logs"]
  source = '''
    parsed = parse_regex!(.message, r'(?P<timestamp>\S+)\s+(?P<level>\S+)\s+(?P<logger>\S+)\s+-\s+(?P<message>.*)')
    . = merge(., parsed ?? {})
    .source_type = "file"
  '''

[sinks.clickhouse]
  type = "clickhouse"
  inputs = ["structured", "unstructured"]
  endpoint = "http://clickhouse:8123"
  table = "logs"
```

### 2.4 ETL 流程

```
原始日志
  │
  ├─ 已是 JSON 格式 ──▶ 字段映射 + 校验 ──▶ ClickHouse
  │
  └─ 非 JSON 格式
       ├─ Regex/Grok 解析
       ├─ 提取 timestamp / level / message
       ├─ 补充 service_name（从文件路径/容器标签推断）
       ├─ 生成 trace_id（如缺失）
       └─ 输出统一 Schema → ClickHouse
```

---

## 3. 日志数据结构设计

### 3.1 ClickHouse 主表

```sql
CREATE TABLE logs (
    -- 时间维度
    timestamp       DateTime64(3)       CODEC(DoubleDelta, ZSTD),
    date            Date                MATERIALIZED toDate(timestamp),

    -- 服务标识
    service_name    LowCardinality(String),
    service_instance String,
    source_host     LowCardinality(String),
    source_type     LowCardinality(String),   -- 'stdout' | 'file' | 'http'

    -- 日志内容
    level           LowCardinality(String),   -- TRACE/DEBUG/INFO/WARN/ERROR/FATAL
    logger          String,
    message         String                   CODEC(ZSTD(3)),

    -- 追踪
    trace_id        String,                   -- 32-char hex
    span_id         String,

    -- 异常（可空）
    exception_type      String,
    exception_message   String                CODEC(ZSTD(3)),
    exception_stacktrace String               CODEC(ZSTD(9)),
    error_signature     FixedString(32),      -- MD5，用于聚类

    -- 扩展
    tags            Map(String, String)       CODEC(ZSTD(3)),
    fields          String                    CODEC(ZSTD(3)),  -- JSON string

    -- 元数据
    ingestion_time  DateTime64(3)             DEFAULT now()
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(date)
ORDER BY (service_name, level, timestamp)
TTL date + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;
```

### 3.2 索引设计

```sql
-- 布隆过滤器索引：加速关键字搜索
ALTER TABLE logs ADD INDEX idx_message message TYPE tokenbf_v1(32768, 3, 0) GRANULARITY 4;

-- 跳数索引：加速 trace_id 精确查找
ALTER TABLE logs ADD INDEX idx_trace_id trace_id TYPE bloom_filter() GRANULARITY 4;

-- 物化列 + 跳数索引：加速异常查询
ALTER TABLE logs ADD INDEX idx_exception_type error_signature TYPE bloom_filter() GRANULARITY 1;
ALTER TABLE logs ADD INDEX idx_exception_msg exception_message TYPE tokenbf_v1(32768, 3, 0) GRANULARITY 4;
```

### 3.3 聚类结果表

```sql
CREATE TABLE error_clusters (
    signature       FixedString(32),     -- MD5(exception_type + normalized_message)
    exception_type  String,
    normalized_msg  String,              -- 去除变量后的错误消息模板
    sample_message  String,              -- 一条原始错误消息样例
    service_name    LowCardinality(String),
    first_seen      DateTime64(3),
    last_seen       DateTime64(3),
    total_count     UInt64,
    date            Date
)
ENGINE = SummingMergeTree(total_count)
PARTITION BY toYYYYMM(date)
ORDER BY (date, service_name, signature)
TTL date + INTERVAL 90 DAY;
```

### 3.4 trace_id 设计

```
格式: {32位hex} = {8位时间戳hex}{8位机器标识hex}{16位随机hex}
示例: 683b2f01 00000001 deadbeefcafebabe

生成优先级:
  1. 业务系统自行生成并传入（W3C TraceContext 兼容）
  2. Vector 在采集层自动补全（基于 container_id + timestamp + random）
```

---

## 4. 错误聚类与分析设计

### 4.1 异常签名提取（纯工程方法）

```
输入: exception_type + exception_message + exception_stacktrace(前3帧)

Step 1 — 消息归一化:
  "insufficient stock for item 42"       → "insufficient stock for item <N>"
  "Connection refused to 192.168.1.100"  → "Connection refused to <IP>"
  "Timeout on request abc-def-123"       → "Timeout on request <UUID>"
  "User 'admin' login failed"            → "User '<STR>' login failed"

  替换规则（正则, 按顺序执行）:
  - UUID/GUID         → <UUID>
  - IP地址            → <IP>
  - 数字(>4位)        → <N>
  - 引号内字符串      → <STR>
  - 路径 (/xxx/yyy)   → <PATH>

Step 2 — 生成签名:
  signature = MD5(exception_type + "\0" + normalized_message + "\0" + stacktop_3frames)

Step 3 — 写入 error_clusters 表（SummingMergeTree 自动聚合 count）
```

### 4.2 聚类逻辑（Python 定时任务）

```python
# 每 5 分钟运行一次
# 1. 从 logs 表读取 error_signature 为空的 ERROR 日志
# 2. 逐条计算 signature，UPDATE 回 logs 表
# 3. INSERT INTO error_clusters (signature, count=1) …
#    SummingMergeTree 自动合并相同 signature 的计数

# 输出:
# - Top 10 errors (按 total_count DESC)
# - 新增错误（first_seen > now() - 1h）
# - 已恢复错误（last_seen < now() - 24h 且之前高频）
```

### 4.3 慢接口分析

```sql
-- 基于 fields.duration_ms 字段，需业务方在日志中携带
SELECT
    service_name,
    logger,
    quantile(0.50)(fields_duration_ms) AS p50,
    quantile(0.95)(fields_duration_ms) AS p95,
    quantile(0.99)(fields_duration_ms) AS p99,
    max(fields_duration_ms) AS max_ms,
    count() AS total_calls
FROM logs
WHERE fields_duration_ms > 0
  AND timestamp >= now() - INTERVAL 24 HOUR
GROUP BY service_name, logger
ORDER BY p95 DESC
LIMIT 20;
```

### 4.4 趋势分析

```sql
-- 按小时聚合日志量 & 错误率
SELECT
    toStartOfHour(timestamp) AS hour,
    service_name,
    count() AS total,
    countIf(level = 'ERROR') AS errors,
    errors / total AS error_rate
FROM logs
WHERE timestamp >= now() - INTERVAL 24 HOUR
GROUP BY hour, service_name
ORDER BY hour;
```

---

## 5. API 设计

### 5.1 日志查询

```
POST /api/v1/logs/query
Content-Type: application/json

{
  "service_name": "order-service",       // 可选，不传=全服务
  "level": "ERROR",                      // 可选
  "keyword": "NullPointerException",     // 可选，全文搜索 message
  "trace_id": "a1b2c3d4...",            // 可选，精确追踪
  "start_time": "2026-05-24T00:00:00Z", // 必填
  "end_time": "2026-05-24T23:59:59Z",   // 必填，默认 now
  "page": 1,                             // 默认 1
  "page_size": 50,                       // 默认 50, 最大 200
  "sort": "desc"                         // asc/desc
}

Response 200:
{
  "total": 1234,
  "page": 1,
  "page_size": 50,
  "items": [ { ...log_entry... } ]
}
```

### 5.2 错误统计

```
GET /api/v1/errors/top?range=24h&limit=10&service_name=order-service

Response 200:
{
  "items": [
    {
      "signature": "a1b2...",
      "exception_type": "IllegalStateException",
      "normalized_msg": "insufficient stock for item <N>",
      "sample_message": "insufficient stock for item 42",
      "service_name": "order-service",
      "total_count": 567,
      "first_seen": "2026-05-24T08:00:00Z",
      "last_seen": "2026-05-24T19:59:00Z",
      "trend": "rising"                   // rising / stable / falling
    }
  ]
}
```

### 5.3 服务状态

```
GET /api/v1/services

Response 200:
{
  "items": [
    {
      "name": "order-service",
      "last_log_at": "2026-05-24T19:59:00Z",
      "log_rate_per_min": 120.5,
      "error_rate": 0.023,
      "status": "healthy"                 // healthy / warning / critical / silent
    }
  ]
}
```

### 5.4 统计分析

```
GET /api/v1/stats/overview?range=24h

Response 200:
{
  "total_logs": 1234567,
  "total_errors": 12345,
  "error_rate": 0.01,
  "service_count": 5,
  "log_volume_trend": [
    { "hour": "2026-05-24T00:00:00Z", "total": 50000, "errors": 500 },
    ...
  ],
  "top_errors": [ ... ],
  "active_services": 5,
  "silent_services": 1
}
```

### 5.5 聚类分析

```
GET /api/v1/errors/clusters?range=7d&service_name=order-service

Response 200:
{
  "total_clusters": 42,
  "new_clusters_24h": 3,
  "resolved_clusters_24h": 5,
  "items": [
    {
      "signature": "...",
      "exception_type": "...",
      "normalized_msg": "...",
      "total_count": 1234,
      "occurrence_days": 5,
      "avg_per_day": 246.8
    }
  ]
}
```

---

## 6. MVP 版本规划

### Phase 1 — 最小可运行（2-3 周）

**目标**：Docker Compose 一键启动，日志进得来、查得出、看得到

| 功能 | 说明 |
|------|------|
| Vector 采集 | Docker stdout + 文件日志双通道 |
| ClickHouse 存储 | 单表 logs，30 天 TTL |
| Spring Boot API | `/api/v1/logs/query` 基础查询 |
| Grafana 仪表盘 | 2 个预置面板：日志概览 + 错误趋势 |
| PostgreSQL 元数据 | services 表 + 服务注册 API |
| 日志结构标准化 | Vector ETL → 统一 JSON Schema |
| Docker Compose | 5 个容器，一键部署 |

**交付物**：
- `docker-compose.yml`
- `vector/vector.toml`
- `clickhouse/init.sql`
- `logsys-api.jar` + `Dockerfile`
- `grafana/dashboards/overview.json`

### Phase 2 — 分析能力（2 周）

| 功能 | 说明 |
|------|------|
| Python 错误聚类 | 定时任务，异常签名提取 + 聚合 |
| Top Error API | `/api/v1/errors/top` |
| 统计分析 API | `/api/v1/stats/overview` |
| 慢接口分析 | 基于 fields.duration_ms |
| 服务健康面板 | error_rate + log_rate 监控 |
| 企业级前端 | 日志查询、错误面板、统计仪表盘、服务管理，专业前端负责开发 |

### Phase 3 — AI 扩展（2-3 周）

| 功能 | 说明 |
|------|------|
| AI 日报 | 每日汇总：错误趋势 + 异常摘要 + 建议 |
| 根因分析 | 输入 error_clusters + 上下文日志 → LLM 分析 |
| 自动建议 | 基于历史聚类结果推荐修复方向 |

---

## 7. 开源项目结构

```
logsys/
├── docker-compose.yml
├── .env.example
├── Makefile                          # make up / make down / make reset
├── README.md
│
├── vector/
│   ├── Dockerfile                    # 极少需要定制
│   └── vector.toml
│
├── clickhouse/
│   ├── init.sql                      # 建表 + 索引
│   └── config.xml                    # 持久化配置
│
├── postgres/
│   └── init.sql                      # 元数据表
│
├── logsys-api/                       # Spring Boot 主系统
│   ├── pom.xml
│   ├── src/main/java/com/logsys/
│   │   ├── LogsysApplication.java
│   │   ├── controller/
│   │   │   ├── LogQueryController.java
│   │   │   ├── ErrorController.java
│   │   │   ├── StatsController.java
│   │   │   └── ServiceController.java
│   │   ├── service/
│   │   ├── repository/               # ClickHouse + PostgreSQL DAO
│   │   ├── model/
│   │   │   ├── LogEntry.java
│   │   │   ├── ErrorCluster.java
│   │   │   └── StatsOverview.java
│   │   └── config/
│   │       ├── ClickHouseConfig.java
│   │       └── PostgresConfig.java
│   └── Dockerfile
│
├── logsys-analyzer/                  # Python 分析模块
│   ├── pyproject.toml
│   ├── main.py                       # 调度入口
│   ├── cluster/
│   │   ├── signature.py              # 异常签名提取
│   │   └── normalizer.py             # 消息归一化
│   ├── stats/
│   │   └── aggregator.py             # 统计计算
│   └── Dockerfile
│
├── logsys-ui/                        # Phase 2 - 专业前端负责
│   ├── package.json
│   ├── next.config.js
│   ├── tailwind.config.ts
│   ├── tsconfig.json
│   ├── public/
│   ├── src/
│   │   ├── app/                      # Next.js App Router（文件系统路由）
│   │   │   ├── layout.tsx            # 根布局
│   │   │   ├── providers.tsx         # 全局 Provider（QueryClient + MSW）
│   │   │   ├── dashboard/page.tsx    # Overview Dashboard
│   │   │   ├── logs/page.tsx         # Log Explorer
│   │   │   ├── errors/page.tsx       # Error Cluster
│   │   │   └── services/[name]/      # Service Detail（动态路由）
│   │   ├── components/               # UI 组件 + 布局组件
│   │   │   ├── ui/                   # 自定义 shadcn 组件（按钮/输入框/表格/卡片…）
│   │   │   └── layout/               # Sidebar / Navbar
│   │   ├── features/                 # 业务功能：log-search / error-cluster / service-health
│   │   ├── entities/                 # 领域模型：Log / Error / Service / Stats
│   │   ├── shared/                   # 通用工具
│   │   │   ├── lib/                  # API client / formatters / constants
│   │   │   └── styles/               # 全局样式 + CSS 变量
│   │   │   └── styles/               # 全局样式 + CSS 变量 + Tailwind 扩展
│   │   └── styles/
│   └── Dockerfile
│
├── grafana/
│   ├── dashboards/
│   │   ├── overview.json
│   │   └── errors.json
│   └── datasources/
│       └── clickhouse.yml
│
└── docs/
    ├── architecture.md
    ├── quickstart.md
    └── log-format-spec.md            # 日志格式规范（给业务方看）
```

---

## 8. 前端设计规范（Phase 2 — 专业前端负责）

### 8.1 技术栈

| 类别 | 选型 | 说明 |
|------|------|------|
| 框架 | Next.js 14 | React 全栈框架，App Router 文件系统路由 |
| 语言 | TypeScript | 强类型，接口契约清晰 |
| 样式 | TailwindCSS 3 | 原子化，与 UI 规范天然对应 |
| 组件基座 | shadcn/ui | 仅用结构，不做默认外观 |
| 状态管理 | Zustand | 轻量，无 boilerplate |
| 服务端状态 | TanStack Query | 缓存 + 请求管理 |
| 图表 | Recharts | 声明式，React 原生 |
| 动效 | Framer Motion | 轻量微交互 |
| 图标 | Lucide Icons | 统一源，体积小 |

### 8.2 设计语言

**视觉基调**：专业、冷静、结构化、工程感。参考 Linear / Vercel Dashboard / Grafana Cloud / Datadog / Raycast。

**禁止风格**：霓虹赛博朋克、紫色 AI 启动页、玻璃拟态、Dribbble 概念稿、廉价 SaaS 模板。

#### 配色系统

- **主色**：中性黑/白/灰，暗色背景为主。强调色**极少使用**。
- **状态色**：

| 类型 | 颜色 |
|------|------|
| ERROR | 柔和红 (soft red) |
| WARN | 琥珀 (amber) |
| SUCCESS | 深绿 (dark green) |
| INFO | 灰蓝 (gray blue) |

- **禁止**：彩虹图表、大背景渐变、饱和紫色、霓虹发光、亮青色。

#### 字体

- **主字体**：Inter / SF Pro Display
- **等宽字体**：JetBrains Mono / IBM Plex Mono（所有日志和指标必须使用等宽）

#### 间距与布局

- 大留白、强层级、精确间距、可控密度
- **禁止**：拥挤的管理后台布局、超大卡片、巨型内边距、过度阴影

#### 圆角

- 仅 `rounded-md` / `rounded-lg`
- **禁止** `rounded-3xl`、到处 `rounded-full`

#### 阴影

- 极其克制，用对比 + 边框 + 分层替代大阴影
- `shadow-sm` 最多

#### 边框

```css
border-white/10
border-neutral-800
```

#### 动画

- 快 + 轻 + 功能性
- `opacity` / `translateY(2px)` / `150ms ease-out`
- **禁止**：bounce / elastic / 过度缩放 / 浮动卡片

### 8.3 页面清单

#### 1. Overview Dashboard
服务状态、错误趋势、请求量、慢 API、Top Errors、健康评分。网格布局，强信息层级。

#### 2. Log Explorer
实时日志流、服务/级别/时间范围筛选、关键字搜索、traceId 追踪。**终端风格、等宽字体为主、极高可读性**。

#### 3. Error Cluster Page
Top 错误组、频率趋势、影响服务、最近出现时间。结构化数据展示。

#### 4. Service Detail Page
服务健康指标 + 最近日志 + 请求趋势。单服务深度视图。

### 8.4 重要组件定制要求

以下组件在 shadcn 基础上**必须自定义**，不可使用默认样式：

Button / Input / Select / Table / Card / Tabs / Sidebar / Navbar / Dialog / Badge / Toast / Chart 容器

**表格规则**：
- 等宽字体支持、紧凑行高、粘性表头、柔和分割线
- **禁止**：斑马条纹、超大行高

**图表规则**：
- 折线图 / 面积图 / 热力图 / 时序图为主
- **禁止**：饼图泛滥、3D 图表、花哨渐变

### 8.5 暗色模式

暗色模式为主模式。参考 macOS 暗色 / Xcode / Linear Dark。用柔和深灰而非纯黑。

### 8.6 代码结构（Feature-Sliced Design）

```
src/
├── app/          # 入口、路由、Provider
├── pages/        # 页面组件
├── widgets/      # 页面级组合块
├── features/     # 业务功能模块
├── entities/     # 领域模型
├── shared/       # 通用 UI + 工具
│   ├── ui/       # 自定义组件
│   ├── lib/      # 工具函数
│   └── styles/   # 全局样式
└── styles/
```

### 8.7 组件开发契约

后端 API 返回的 JSON 结构即前端 Props 的 source of truth。前后端通过 OpenAPI / Swagger 文档对齐。专业前端拿到 API 文档后自行决定组件内部分割。

---

## 9. AI 扩展方案（简要）

### 为什么不能直接分析原始日志

1. **Token 爆炸**：一天百万条日志，直接喂 LLM 成本不可接受
2. **噪声淹没信号**：99% 的 INFO/DEBUG 对 AI 分析无意义
3. **上下文碎片化**：单条日志缺少"全局图景"

### AI 应该分析的数据

| 数据层 | 输入 | AI 产出 |
|--------|------|---------|
| **聚类摘要** | error_clusters 表的 Top N + 趋势 | 错误日报：「今日新增 3 类错误，其中 X 影响最大」 |
| **错误上下文** | 单个 cluster 的 sample_message + 前后 10 条日志 | 根因假设：「可能由 DB 连接池耗尽引发」 |
| **时序模式** | log_volume_trend + error_rate 时序 | 异常检测：「凌晨 3 点出现异常日志尖峰」 |
| **服务拓扑** | 跨服务 trace_id 串联的日志 | 影响面分析：「该错误波及 3 个下游服务」 |

### 数据流

```
ClickHouse
  ├─ error_clusters (聚合) ──▶ AI 日报 / 周报
  ├─ logs (按 signature 采样) ──▶ 根因分析 Prompt
  └─ stats 聚合结果 ──▶ 异常检测模型 (统计方法 + LLM 验证)
```

### 方案建议

- Phase 3 首选 **Claude API / OpenAI API** 直接调用，不做本地模型部署
- Prompt 输入控制在 2000 token 以内（精选的聚类数据 + 上下文日志）
- 日报通过定时 Python 脚本生成，输出 Markdown 或推送到飞书/钉钉

---

## 10. 风险点

| 风险 | 等级 | 应对 |
|------|------|------|
| ClickHouse 内存占用超预期 | 中 | 配置 `max_server_memory_usage` 限制，最低可跑在 512MB |
| 非 JSON 日志解析不准确 | 高 | MVP 只承诺常见格式（log4j 默认、Python logging 默认），其余需用户自行提供 regex pattern |
| Vector 配置复杂度 | 中 | 提供开箱即用的 vector.toml，覆盖 80% 场景 |
| 用户不愿意改日志格式 | 高 | 接受原始文本日志，Vector regex 解析兜底，但会丢失 fields/tags 等高级字段 |
| 单机磁盘爆满 | 中 | ClickHouse TTL 30 天，Vector buffer 限制 256MB 磁盘 |
| Postgres 引入增加运维负担 | 低 | 可考虑 Phase 1 用 SQLite 替代，Phase 2 再切 Postgres |

---

## 11. 关键设计决策

1. **为什么 ClickHouse 而不是 Elasticsearch** — ClickHouse 单机写入性能远优于 ES，内存占用更低（512MB vs 2GB+），且压缩率高（10:1 vs 2:1）
2. **为什么 Vector 而不是 Fluent Bit** — Vector 配置更简洁（TOML 而非 Lua 插件），Rust 生态，内存更可控
3. **为什么 Spring Boot 而不是 Go** — 用户技术栈指定 Java，且 Spring Boot 生态成熟，MyBatis 对接 ClickHouse 简单
4. **Grafana 先行，Phase 2 专业前端接手** — Grafana 做 MVP 零成本可视；Phase 2 由专业前端用 React 重写全部 UI，Grafana 降级为备选数据源
5. **异常签名用 MD5 而非 ML** — 确定性算法，零依赖，可解释，资源零消耗。后续 AI 阶段再增强

---

## 12. 最小可运行方案（MVP-1 具体步骤）

### 开发者启动流程

```bash
git clone https://github.com/xxx/logsys.git
cd logsys
cp .env.example .env
# 可选：修改 CLICKHOUSE_PASSWORD, POSTGRES_PASSWORD
docker compose up -d
# 打开 http://localhost:3000 看 Grafana
# 打开 http://localhost:8080/api/v1/services 验证 API
```

### 业务方接入（以 Java 为例）

```xml
<!-- logback-spring.xml -->
<appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

只需引入 `logstash-logback-encoder` 一个依赖，不改任何代码。日志以 JSON 输出到 stdout，Docker 自动采集，Vector 发现后直接入库。

### 资源预算（单机最低配置）

| 容器 | 内存 | CPU |
|------|------|-----|
| Vector | 128MB | 0.5 |
| ClickHouse | 512MB | 1.0 |
| PostgreSQL | 128MB | 0.5 |
| logsys-api | 256MB | 0.5 |
| Grafana | 128MB | 0.5 |
| **合计** | **~1.2GB** | **3.0** |
