> plan: logsys-platform-mvp
> phase: schema
> status: confirmed
> base: focus-spec.md §3, api-spec.md

# 数据库 Schema 契约

---

# 一、ClickHouse — 日志存储

## 1.1 logs 表（主日志表）

```sql
-- ============================================================
-- 表：logs
-- 引擎：MergeTree，按天分区，30 天 TTL
-- 写入：Vector → ClickHouse HTTP Interface
-- 读取：Spring Boot → /api/v1/logs/query
-- 预估写入量：~1000 rows/s（单机 5 个服务峰值）
-- ============================================================

CREATE TABLE IF NOT EXISTS logs (
    -- ── 时间维度 ──
    timestamp         DateTime64(3)   NOT NULL  COMMENT '日志产生时间（业务侧时间戳）',
    date              Date            NOT NULL  COMMENT '分区键，由 timestamp 自动生成',

    -- ── 服务标识 ──
    service_name      String          NOT NULL  COMMENT '服务名，如 order-service。来源：业务日志 JSON 或 Vector 推断',
    service_instance  String          DEFAULT '' COMMENT '实例标识，如 Pod 名 / container_id',
    source_host       String          DEFAULT '' COMMENT '宿主机名或 IP',
    source_type       String          DEFAULT '' COMMENT '采集来源：stdout | file | http',

    -- ── 日志内容 ──
    level             String          NOT NULL  COMMENT '日志级别：TRACE | DEBUG | INFO | WARN | ERROR | FATAL',
    logger            String          DEFAULT '' COMMENT 'Logger 名或模块名，如 com.example.OrderService',
    message           String          NOT NULL  COMMENT '日志正文。全文搜索目标字段',

    -- ── 追踪 ──
    trace_id          String          DEFAULT '' COMMENT '32 位 hex 分布式追踪 ID，用于跨服务日志串联',
    span_id           String          DEFAULT '' COMMENT '16 位 hex Span ID',

    -- ── 异常信息（仅 ERROR 及以上有值） ──
    exception_type       String       DEFAULT '' COMMENT '异常类名，如 java.lang.NullPointerException',
    exception_message    String       DEFAULT '' COMMENT '异常消息，去除变量后的可聚类文本',
    exception_stacktrace String       DEFAULT '' COMMENT '完整异常栈，可能 10KB+',
    error_signature      FixedString(32) DEFAULT '' COMMENT '异常签名 MD5，用于聚类。由 Python 分析模块回填',

    -- ── 扩展字段 ──
    tags         Map(String, String)  COMMENT '业务标签：order_id、user_id、request_path 等。键值对',
    fields       Map(String, String)  COMMENT '结构化指标：duration_ms、db_rows、cache_hit 等。值统一存为 String',

    -- ── 元数据 ──
    ingestion_time DateTime64(3) DEFAULT now() COMMENT 'Vector 写入 ClickHouse 的时间，非日志产生时间'

) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(date)
ORDER BY (service_name, level, toUnixTimestamp(timestamp))
TTL date + INTERVAL 30 DAY DELETE
SETTINGS index_granularity = 8192
COMMENT '统一日志存储主表。按天分区，主键为(service_name, level, timestamp)，30 天自动清理';
```

### 分区策略说明

- `PARTITION BY toYYYYMMDD(date)`：按天分区，每天一个物理目录
- TTL 30 天：30 天前的分区整块删除（DROP PARTITION），零成本清理，无需 DELETE 语句
- 优点：TTL 到期直接删分区，不产生 DELETE 标记，不影响查询性能

### ORDER BY 设计说明

`(service_name, level, timestamp)`：

| 查询场景 | 命中方式 |
|----------|----------|
| `WHERE service_name = ? AND level = ? AND timestamp BETWEEN ? AND ?` | 主键二分查找 + 范围扫描，最优 |
| `WHERE service_name = ? AND timestamp BETWEEN ? AND ?` | 主键前缀匹配，良好 |
| `WHERE trace_id = ?` | 不走主键，走 bloom_filter 索引 |
| `WHERE keyword IN message` | 不走主键，走 tokenbf_v1 索引 |

### 字段设计决策

| 决策 | 原因 |
|------|------|
| tags/fields 用 `Map(String, String)` | 不用 JSON String，因为 ClickHouse 对 Map 有原生查询函数 `mapContains()` `mapKeys()` `mapValues()` |
| error_signature 用 `FixedString(32)` | MD5 固定 32 字符，比 String 少 8 字节开销 / 行 |
| message/exception_stacktrace 不用 CODEC | CODEC 留到表创建后 ALTER 添加，建表语句保持干净；实际部署时按需加 ZSTD(3) |
| `ingestion_time` 独立于 `timestamp` | 业务时间戳可能不准/缺失，ingestion_time 作为兜底排序依据 |

---

## 1.2 索引（建表后执行）

```sql
-- ============================================================
-- 索引 1：message 全文搜索加速
-- 对应查询：WHERE message LIKE '%keyword%'
-- 类型：tokenbf_v1（布隆过滤器分词索引）
-- 参数：32768 bytes = 32KB 布隆过滤器
--       3 = 3 个哈希函数
--       0 = 不分词（按空格/NLP 分词），0 表示按 ngram 切分
-- GRANULARITY 4 = 每 4 个 granule（4×8192=32768 行）建一个索引块
-- ============================================================
ALTER TABLE logs
ADD INDEX idx_message message TYPE tokenbf_v1(32768, 3, 0) GRANULARITY 4;

-- ============================================================
-- 索引 2：trace_id 精确查找加速
-- 对应查询：WHERE trace_id = 'a1b2c3...'
-- 类型：bloom_filter（精确匹配布隆）
-- GRANULARITY 4：trace_id 分布均匀，可适当稀疏
-- ============================================================
ALTER TABLE logs
ADD INDEX idx_trace_id trace_id TYPE bloom_filter() GRANULARITY 4;

-- ============================================================
-- 索引 3：error_signature 精确查找加速
-- 对应查询：WHERE error_signature = '...'
-- GRANULARITY 1：错误日志占比低（<5%），索引可精细
-- ============================================================
ALTER TABLE logs
ADD INDEX idx_error_signature error_signature TYPE bloom_filter() GRANULARITY 1;

-- ============================================================
-- 索引 4：exception_message 搜索加速
-- 对应查询：WHERE exception_message LIKE '%timeout%'
-- ============================================================
ALTER TABLE logs
ADD INDEX idx_exception_msg exception_message TYPE tokenbf_v1(32768, 3, 0) GRANULARITY 4;
```

---

## 1.3 error_clusters 表（错误聚类聚合表）

```sql
-- ============================================================
-- 表：error_clusters
-- 引擎：SummingMergeTree（相同主键自动合并 total_count）
-- 写入：Python 分析模块定时写入（每 5 分钟）
-- 读取：Spring Boot → /api/v1/errors/top, /api/v1/errors/clusters
-- ============================================================

CREATE TABLE IF NOT EXISTS error_clusters (
    -- ── 签名 ──
    signature         FixedString(32)  COMMENT '异常签名 MD5 = MD5(exception_type + \0 + normalized_msg + \0 + stacktop_3frames)',

    -- ── 聚类标签 ──
    exception_type    String           COMMENT '异常类名',
    normalized_msg    String           COMMENT '去除变量后的消息模板，如 insufficient stock for item <N>',
    sample_message    String           COMMENT '一条原始消息样例，用于展示',

    -- ── 归属 ──
    service_name      String           COMMENT '来源服务',

    -- ── 时间与计数 ──
    first_seen        DateTime64(3)    COMMENT '该签名首次出现时间',
    last_seen         DateTime64(3)    COMMENT '该签名最近出现时间',
    total_count       UInt64           COMMENT '该签名总出现次数（SummingMergeTree 自动聚合）',

    -- ── 分区 ──
    date              Date             COMMENT '分区键'

) ENGINE = SummingMergeTree(total_count)
PARTITION BY toYYYYMM(date)
ORDER BY (date, service_name, signature)
TTL date + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192
COMMENT '错误聚类聚合表。SummingMergeTree 引擎，相同 ORDER BY 键自动 sum total_count';
```

### SummingMergeTree 行为说明

```sql
-- Python 每次写入（可重复执行，幂等）：
INSERT INTO error_clusters
  (signature, exception_type, normalized_msg, sample_message,
   service_name, first_seen, last_seen, total_count, date)
VALUES
  ('abc123...', 'NullPointerException', '...', '...', 'order-service',
   '2026-05-24T08:00:00', '2026-05-24T09:00:00', 1, '2026-05-24');

-- 查询时自动聚合（后台 merge 或查询时 GROUP BY）：
SELECT
    signature,
    any(exception_type) AS exception_type,
    any(normalized_msg) AS normalized_msg,
    any(sample_message) AS sample_message,
    any(service_name) AS service_name,
    min(first_seen) AS first_seen,
    max(last_seen) AS last_seen,
    sum(total_count) AS total_count
FROM error_clusters
WHERE date >= ? AND date <= ?
GROUP BY signature
ORDER BY total_count DESC
LIMIT 10;
```

### error_clusters 表的备选方案（Phase 1）

Phase 1 没有 Python 分析模块时，Spring Boot 可直接查 logs 表做实时聚合：

```sql
-- 降级方案：从 logs 表实时聚合同类错误
SELECT
    exception_type,
    exception_message,
    count() AS total_count,
    min(timestamp) AS first_seen,
    max(timestamp) AS last_seen,
    groupUniqArray(service_name) AS affected_services
FROM logs
WHERE level = 'ERROR'
  AND exception_type != ''
  AND timestamp >= ? AND timestamp <= ?
GROUP BY exception_type, exception_message
ORDER BY total_count DESC
LIMIT ?
```

此降级查询不带 normalized_msg（无归一化），`exception_type + exception_message` 相同时才归为一组。误差可接受，Phase 2 引入 Python 归一化后自动改读 error_clusters 表。

---

# 二、PostgreSQL — 元数据存储

## 2.1 services 表

```sql
-- ============================================================
-- 表：services
-- 用途：服务注册与元数据管理
-- 写入：POST /api/v1/services（手动）或 Vector 自动发现后调用
-- 读取：GET /api/v1/services, GET /api/v1/services/{name}
-- ============================================================

CREATE TABLE IF NOT EXISTS services (
    id              SERIAL          PRIMARY KEY,
    name            VARCHAR(64)     NOT NULL UNIQUE,  -- 服务名，如 order-service
    display_name    VARCHAR(128)    DEFAULT '',        -- 展示名，如 订单服务
    description     VARCHAR(512)    DEFAULT '',        -- 服务描述
    language        VARCHAR(16)     DEFAULT 'other',   -- java | python | nodejs | go | other
    repository_url  VARCHAR(256)    DEFAULT '',        -- 代码仓库 URL
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_services_name CHECK (name ~ '^[a-z][a-z0-9-]{2,63}$'),
    CONSTRAINT chk_services_language CHECK (language IN ('java', 'python', 'nodejs', 'go', 'other'))
);

COMMENT ON TABLE services IS '服务注册表。name 为唯一键，由 Vector 自动发现或手动注册';
COMMENT ON COLUMN services.name IS '服务唯一标识，仅小写字母/数字/连字符，3-64 字符';
COMMENT ON COLUMN services.language IS '编程语言，来自 POST /services 请求体或 Vector 推断';
COMMENT ON COLUMN services.repository_url IS '代码仓库 URL，可选，方便开发者跳转';

-- 唯一约束补充（显式命名，方便错误信息引用）
-- 重复注册返回 409: { "code": "CONFLICT", "message": "Service 'xxx' already exists" }

-- 触发器：自动更新 updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trg_services_updated_at
    BEFORE UPDATE ON services
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

### 设计说明

- 没有 `is_deleted` 字段 — focus-spec 禁止物理删除，但元数据表不需要软删除（服务注销即直接删除，不影响日志查询）
- PostgreSQL 仅存元数据，不做日志查询的缓存（ClickHouse 查询足够快）
- `repository_url` 可选，为后续「从日志跳转到源码」预留

---

## 2.2 可选：cached_stats 表（Phase 2 引入）

```sql
-- ============================================================
-- 表：cached_stats
-- 用途：Python 分析模块定时预聚合统计结果，API 直接读此表而无需每次查 ClickHouse
-- 写入：Python 分析模块（每 5 分钟全量替换）
-- 读取：Spring Boot → GET /api/v1/stats/overview
-- Phase 1 不创建此表，Phase 2 随 Python 模块一起上线
-- ============================================================

CREATE TABLE IF NOT EXISTS cached_stats (
    stat_key        VARCHAR(64)     NOT NULL,   -- 统计键，如 overview_24h / service_trend_order-service_1h
    stat_type       VARCHAR(32)     NOT NULL,   -- overview | service_trend | error_trend
    payload         JSONB           NOT NULL,   -- 聚合结果 JSON
    computed_at     TIMESTAMPTZ     NOT NULL,   -- 计算时间
    expires_at      TIMESTAMPTZ     NOT NULL,   -- 过期时间

    PRIMARY KEY (stat_key)
);

COMMENT ON TABLE cached_stats IS '统计缓存表。Python 定时计算后写入，API 直接读取';
```

---

# 三、索引与查询性能对照表

| API | ClickHouse 查询特征 | 命中索引 | 预期耗时 |
|-----|---------------------|----------|----------|
| POST /logs/query (按 service+level+time) | 主键二分查找 + 范围扫描 | ORDER BY 键 | < 100ms |
| POST /logs/query (按 keyword) | 全表扫描 message 列 | idx_message (tokenbf) | < 500ms |
| POST /logs/query (按 trace_id) | 精确匹配 | idx_trace_id (bloom) | < 50ms |
| GET /errors/top (从 error_clusters 读) | SummingMergeTree 读 | ORDER BY 键 | < 50ms |
| GET /errors/top (降级方案，从 logs 聚合) | 全表聚合 ERROR 日志 | 无专用索引，利用分区裁剪 | < 2s |
| GET /stats/overview | 全表聚合 | 分区裁剪 | < 1s |
| GET /services | PG 主键扫描 | PK index | < 5ms |

---

# 四、迁移与版本管理

### ClickHouse

ClickHouse 不支持事务 DDL，迁移策略：

- `init.sql` 包含全部 `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ... ADD INDEX IF NOT EXISTS`
- 幂等执行：容器首次启动时自动执行，后续重启跳过
- DDL 变更通过新增 `migrations/clickhouse/002_xxx.sql` 文件管理，文件名含序号

### PostgreSQL

- Phase 1：`init.sql` + Spring Boot 启动时 Flyway 自动迁移
- Phase 2：持续用 Flyway 管理版本

---

# 五、关键设计决策

| 决策 | 原因 |
|------|------|
| logs 表不存 JSON String 而用 Map 类型 | ClickHouse Map 支持 `mapContains()` 原生查询，无需 JSONExtract |
| 分区按天不按月 | 单机场景日志量可控，按天更灵活；TTL 到期删分区粒度更细 |
| ORDER BY 没有把 timestamp 放第一 | service_name + level 过滤性更强，先缩小范围再扫时间区间 |
| error_signature 存储冗余 | 允许 Phase 1 无 Python 时为空，Phase 2 回填；不依赖实时计算 |
| SummingMergeTree 而非 AggregatingMergeTree | 需求仅需 SUM(count)，无需 MIN/MAX/AVG 聚合函数 |
| PG 不用软删除 | 元数据表行数少（服务数 < 100），直接 DELETE 即可 |
