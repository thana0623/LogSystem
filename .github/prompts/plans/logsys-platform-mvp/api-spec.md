> plan: logsys-platform-mvp
> phase: api-spec
> status: confirmed
> base: focus-spec.md §5

# API 接口契约

## 通用约定

### Base URL

```
http://{host}:8080/api/v1
```

### 通用响应头

```
Content-Type: application/json; charset=utf-8
X-Request-Id: {uuid}        ← 每次请求自动生成，方便排查
```

### 通用错误响应格式

```json
{
  "code": "ERROR_CODE",
  "message": "人类可读的错误描述",
  "details": null
}
```

details 为 `null` 时省略，为数组时展开：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求参数校验失败",
  "details": [
    { "field": "start_time", "reason": "must not be empty" },
    { "field": "page_size", "reason": "must be <= 200" }
  ]
}
```

### 错误码枚举

| HTTP Status | code | 触发条件 |
|-------------|------|----------|
| 400 | `BAD_REQUEST` | 缺少必填字段 |
| 400 | `VALIDATION_ERROR` | 参数校验失败 |
| 400 | `TIME_RANGE_TOO_LARGE` | 时间范围超过 7 天 |
| 404 | `NOT_FOUND` | 资源不存在 |
| 500 | `INTERNAL_ERROR` | 服务器内部错误 |
| 503 | `CLICKHOUSE_UNAVAILABLE` | ClickHouse 连接失败 |

### 分页约定

所有列表接口统一分页结构：

```json
{
  "total": 1234,
  "page": 1,
  "page_size": 50,
  "items": []
}
```

- `page` 从 1 开始，传入 ≤0 视为 1
- `page_size` 默认 50，最大 200，传入 >200 截断为 200
- `total` 是满足筛选条件的总记录数，非当前页记录数

### 时间格式

统一 **ISO 8601 UTC**：`2026-05-24T12:00:00.123Z`

---

## 接口清单

| # | 方法 | 路径 | 用途 | 优先级 |
|---|------|------|------|--------|
| 1 | GET | `/health` | 健康检查 | P0 |
| 2 | POST | `/logs/query` | 日志查询 | P0 |
| 3 | GET | `/services` | 服务列表 | P0 |
| 4 | POST | `/services` | 服务注册 | P1 |
| 5 | GET | `/services/{name}` | 服务详情 | P1 |
| 6 | GET | `/errors/top` | Top 错误 | P1 |
| 7 | GET | `/errors/clusters` | 聚类分析 | P1 |
| 8 | GET | `/stats/overview` | 统计概览 | P1 |

---

## 1. GET /health

**用途**：健康检查，Docker Compose 的 `depends_on` 条件。

**请求参数**：无

**响应 200**：
```json
{
  "status": "UP",
  "clickhouse": "UP",
  "postgres": "UP",
  "timestamp": "2026-05-24T12:00:00.123Z"
}
```

**响应 503**：
```json
{
  "status": "DOWN",
  "clickhouse": "DOWN",
  "postgres": "UP",
  "timestamp": "2026-05-24T12:00:00.123Z"
}
```

**后端实现要求**：
- 分别 ping ClickHouse 和 PostgreSQL
- 任一下游不可用返回 503，status 为 `DOWN`
- 响应时间 < 100ms

**前端注意事项**：
- 前端不直接调此接口，仅运维使用

---

## 2. POST /logs/query

**用途**：按条件分页查询日志。前端 Log Explorer 页面的核心数据源。

### 请求体

| 字段 | 类型 | 必填 | 默认值 | 约束 |
|------|------|------|--------|------|
| `service_name` | string | 否 | — | 空=查全部服务 |
| `level` | string | 否 | — | 枚举：TRACE / DEBUG / INFO / WARN / ERROR / FATAL |
| `keyword` | string | 否 | — | 全文搜索 message 字段，最小 2 字符 |
| `trace_id` | string | 否 | — | 精确匹配，32 位 hex |
| `start_time` | string | **是** | — | ISO 8601 UTC |
| `end_time` | string | **是** | — | ISO 8601 UTC，必须 > start_time |
| `page` | number | 否 | 1 | ≥1 |
| `page_size` | number | 否 | 50 | 1-200 |
| `sort` | string | 否 | `desc` | `asc` / `desc` |

### 校验规则

- `start_time` 和 `end_time` 缺一不可
- `end_time` 必须大于 `start_time`
- `end_time - start_time` ≤ 7 天（168 小时）
- `keyword` 长度 ≥ 2（传了但不足 2 → 返回 400，提示太短）
- `level` 不在枚举内 → 无视该条件（不报错，查全部 level）
- `page_size` > 200 → 截断为 200
- `page` < 1 → 视为 1

### 响应 200

```json
{
  "total": 1234,
  "page": 1,
  "page_size": 50,
  "items": [
    {
      "timestamp": "2026-05-24T12:00:00.123Z",
      "service_name": "order-service",
      "service_instance": "order-service-7d8f9-abc12",
      "source_host": "lab-server-01",
      "source_type": "stdout",
      "level": "ERROR",
      "logger": "com.example.OrderService",
      "message": "Failed to create order: insufficient stock for item 42",
      "trace_id": "a1b2c3d4e5f67890",
      "span_id": "1234567890abcdef",
      "exception_type": "java.lang.IllegalStateException",
      "exception_message": "insufficient stock for item 42",
      "exception_stacktrace": "java.lang.IllegalStateException: insufficient stock\n\tat com.example.OrderService.create(OrderService.java:42)\n\tat ...",
      "tags": {
        "order_id": "ORD-12345",
        "user_id": "U-678"
      },
      "fields": {
        "duration_ms": 1450,
        "db_query": "SELECT * FROM orders WHERE id = ?"
      }
    }
  ]
}
```

### 空结果

```json
{
  "total": 0,
  "page": 1,
  "page_size": 50,
  "items": []
}
```

### 响应 400（缺少必填字段）

```json
{
  "code": "BAD_REQUEST",
  "message": "start_time and end_time are required",
  "details": [
    { "field": "start_time", "reason": "must not be null" }
  ]
}
```

### 响应 400（时间范围过大）

```json
{
  "code": "TIME_RANGE_TOO_LARGE",
  "message": "Time range must not exceed 7 days",
  "details": null
}
```

### 后端实现要求

- ClickHouse 查询必须带 `timestamp >= ? AND timestamp <= ?` 条件，**禁止全表扫描**
- `keyword` 搜索使用 `WHERE message LIKE '%keyword%'` 或 ClickHouse 的 `hasToken()` 函数
- `sort` 仅作用于 `timestamp` 字段
- 排序字段和方向必须用参数化方式传入（MyBatis `${}` 需校验白名单，防止 SQL 注入）
- 返回的 `items` 顺序与 `sort` 一致
- `exception_stacktrace` 可能很长（10KB+），前端不一定展示全部，但后端不截断

### 前端注意事项

- `exception_type` / `exception_message` / `exception_stacktrace` 三个字段都可能为 `null`
- `tags` 和 `fields` 为空时返回 `{}`，不是 `null`
- `items` 可能为空数组，必须展示空状态 UI
- 切换筛选条件时重置 `page` 为 1，附带一个 `isPolling` 标记避免重复请求
- 日志行 **必须用等宽字体**（JetBrains Mono）
- 搜索框输入建议 debounce 300ms 再发请求

---

## 3. GET /services

**用途**：获取所有已注册服务及其健康摘要。

### 查询参数

无必填。支持可选过滤：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `status` | string | 否 | — | `healthy` / `warning` / `critical` / `silent` |

### 响应 200

```json
{
  "items": [
    {
      "name": "order-service",
      "description": "订单服务",
      "language": "java",
      "last_log_at": "2026-05-24T19:59:00.123Z",
      "log_rate_per_min": 120.5,
      "error_rate": 0.023,
      "status": "healthy",
      "first_seen": "2026-05-20T08:00:00.000Z",
      "instance_count": 1
    }
  ]
}
```

### 状态判定规则（后端计算）

| status | 条件 |
|--------|------|
| `healthy` | 最近 5 分钟有日志，error_rate ≤ 5% |
| `warning` | 最近 5 分钟有日志，error_rate > 5% |
| `critical` | 最近 5 分钟有日志，error_rate > 20% |
| `silent` | 最近 5 分钟无日志 |

### 响应 200（空）

```json
{
  "items": []
}
```

### 后端实现要求

- 从 PostgreSQL `services` 表读元数据
- `last_log_at` / `log_rate_per_min` / `error_rate` / `status` 通过查 ClickHouse 实时计算
- 无服务时不报错，返回空数组
- 此接口可能被前端定时轮询（每 30 秒），需控制响应时间

### 前端注意事项

- 每个服务卡片根据 `status` 显示对应颜色
- `silent` 状态用灰色标记，提示用户该服务可能挂了
- 点击服务卡片跳转到服务详情页

---

## 4. POST /services

**用途**：注册新服务。由运维脚本或 Vector 自动调用，发现新服务时注册元数据。

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | **是** | 服务唯一标识，如 `order-service` |
| `description` | string | 否 | 服务描述 |
| `language` | string | 否 | `java` / `python` / `nodejs` / `go` / `other` |

### 响应 201

```json
{
  "name": "order-service",
  "description": "订单服务",
  "language": "java",
  "created_at": "2026-05-24T12:00:00.123Z"
}
```

### 响应 409（已存在）

```json
{
  "code": "CONFLICT",
  "message": "Service 'order-service' already exists",
  "details": null
}
```

### 后端实现要求

- `name` 为唯一键，重复注册返回 409（不静默覆盖）
- `name` 格式校验：仅允许小写字母、数字、连字符，3-64 字符，正则 `^[a-z][a-z0-9-]{2,63}$`
- `language` 枚举校验，不在列表内 → 存为 `other`

### 前端注意事项

- Phase 1 前端不需要调此接口
- Phase 2 可在 Settings 页添加手动注册表单

---

## 5. GET /services/{name}

**用途**：获取单个服务的详细指标。

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `name` | string | 服务名 |

### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `range` | string | 否 | `1h` | `15m` / `1h` / `6h` / `24h` / `7d` |

### 响应 200

```json
{
  "name": "order-service",
  "description": "订单服务",
  "language": "java",
  "status": "healthy",
  "first_seen": "2026-05-20T08:00:00.000Z",
  "last_log_at": "2026-05-24T19:59:00.123Z",
  "metrics": {
    "total_logs": 123456,
    "total_errors": 2469,
    "error_rate": 0.02,
    "log_rate_per_min": 120.5
  },
  "log_volume_trend": [
    { "timestamp": "2026-05-24T19:00:00Z", "total": 7200, "errors": 144 },
    { "timestamp": "2026-05-24T19:15:00Z", "total": 7100, "errors": 130 }
  ],
  "top_errors": [
    {
      "signature": "a1b2c3d4...",
      "exception_type": "IllegalStateException",
      "normalized_msg": "insufficient stock for item <N>",
      "count": 567
    }
  ],
  "recent_logs": [
    { "timestamp": "...", "level": "ERROR", "message": "...", "trace_id": "..." }
  ]
}
```

### 响应 404

```json
{
  "code": "NOT_FOUND",
  "message": "Service 'unknown-service' not found",
  "details": null
}
```

### 后端实现要求

- 先从 PostgreSQL 查服务是否存在，不存在返回 404
- `log_volume_trend` 粒度：15m/1h → 按 1 分钟聚，6h → 按 5 分钟聚，24h → 按 15 分钟聚，7d → 按 1 小时聚
- `top_errors` 取前 5
- `recent_logs` 取最近 10 条，含 ERROR + WARN

### 前端注意事项

- `log_volume_trend` 用于画时序面积图
- `recent_logs` 用于页面底部快速预览，点击跳转到 Log Explorer
- `metrics` 用于顶部指标卡片

---

## 6. GET /errors/top

**用途**：获取时间范围内的 Top N 错误聚类。

### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `range` | string | 否 | `24h` | `1h` / `6h` / `24h` / `7d` |
| `limit` | number | 否 | 10 | 1-50 |
| `service_name` | string | 否 | — | 不传=全部服务 |
| `trend` | string | 否 | — | `rising` / `falling` / `stable` |

### 响应 200

```json
{
  "items": [
    {
      "rank": 1,
      "signature": "a1b2c3d4e5f6789012345678abcdef01",
      "exception_type": "java.lang.IllegalStateException",
      "normalized_msg": "insufficient stock for item <N>",
      "sample_message": "insufficient stock for item 42",
      "service_name": "order-service",
      "total_count": 567,
      "first_seen": "2026-05-24T08:00:00.123Z",
      "last_seen": "2026-05-24T19:59:00.456Z",
      "trend": "rising",
      "affected_services": ["order-service", "gateway"]
    }
  ]
}
```

### 趋势判定（后端计算）

| trend | 条件 |
|-------|------|
| `rising` | 最近 1 小时的 count > 前 1 小时的 count × 1.3 |
| `falling` | 最近 1 小时的 count < 前 1 小时的 count × 0.7 |
| `stable` | 其他 |

### 后端实现要求

- 从 `error_clusters` 表读聚合数据
- `limit` 最大 50
- 按 `total_count DESC` 排序
- `affected_services` 是统计该 signature 出现的所有 service_name
- 如果 ClickHouse 的 error_clusters 表为空（Phase 1 无 Python 分析），读 `logs` 表实时聚合：
  ```sql
  SELECT
      exception_type,
      exception_message,
      count() AS total_count,
      min(timestamp) AS first_seen,
      max(timestamp) AS last_seen,
      groupUniqArray(service_name) AS affected_services
  FROM logs
  WHERE level = 'ERROR'
    AND timestamp >= ? AND timestamp <= ?
  GROUP BY exception_type, exception_message
  ORDER BY total_count DESC
  LIMIT ?
  ```

### 前端注意事项

- `trend === 'rising'` 显示红色向上箭头
- `trend === 'falling'` 显示绿色向下箭头
- 点击某条错误跳转到 Log Explorer，携带 `exception_type` 作为筛选条件
- `sample_message` 展示时截断到 120 字符，hover 展开全文
- 接口可能返回空数组（无错误时段），展示空状态

---

## 7. GET /errors/clusters

**用途**：获取错误聚类的分析概览（比 `/errors/top` 更宏观）。

### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `range` | string | 否 | `7d` | `24h` / `7d` / `30d` |
| `service_name` | string | 否 | — | — |

### 响应 200

```json
{
  "summary": {
    "total_clusters": 42,
    "total_errors": 12345,
    "new_clusters_24h": 3,
    "resolved_clusters_24h": 5
  },
  "daily_trend": [
    { "date": "2026-05-18", "total_errors": 1200, "unique_clusters": 35 },
    { "date": "2026-05-19", "total_errors": 1350, "unique_clusters": 38 }
  ],
  "top_clusters": [
    {
      "rank": 1,
      "signature": "...",
      "exception_type": "...",
      "normalized_msg": "...",
      "total_count": 1234,
      "occurrence_days": 5,
      "avg_per_day": 246.8,
      "first_seen": "2026-05-20T08:00:00Z",
      "last_seen": "2026-05-24T19:59:00Z"
    }
  ]
}
```

### 后端实现要求

- `summary` 从 `error_clusters` 表聚合
- `new_clusters_24h` = `first_seen > now() - 24h` 的 cluster 数
- `resolved_clusters_24h` = `last_seen < now() - 24h` 且之前 high-frequency 的 cluster 数
- `daily_trend` 按天聚合，最多返回 `range` 对应的天数

### 前端注意事项

- `summary` 顶部放 4 个指标卡片
- `daily_trend` 画面积图（错误总量 + 聚类数双 Y 轴）
- 此接口用于 Overview Dashboard + Error Cluster 页面

---

## 8. GET /stats/overview

**用途**：全局统计概览，Overview Dashboard 的数据源。

### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `range` | string | 否 | `24h` | `1h` / `6h` / `24h` / `7d` |

### 响应 200

```json
{
  "total_logs": 1234567,
  "total_errors": 12345,
  "error_rate": 0.01,
  "service_count": 5,
  "active_services": 4,
  "silent_services": 1,
  "log_volume_trend": [
    { "timestamp": "2026-05-24T00:00:00Z", "total": 50000, "errors": 500 },
    { "timestamp": "2026-05-24T01:00:00Z", "total": 48000, "errors": 480 }
  ],
  "level_distribution": {
    "DEBUG": 800000,
    "INFO": 350000,
    "WARN": 50000,
    "ERROR": 12345,
    "FATAL": 2
  },
  "top_services_by_volume": [
    { "service_name": "order-service", "total_logs": 500000 },
    { "service_name": "gateway", "total_logs": 400000 }
  ],
  "top_errors": [
    {
      "signature": "...",
      "exception_type": "IllegalStateException",
      "normalized_msg": "insufficient stock for item <N>",
      "count": 567,
      "service_name": "order-service"
    }
  ]
}
```

### 后端实现要求

- 所有聚合走 ClickHouse
- `log_volume_trend` 粒度：range ≤1h → 1min，≤6h → 5min，≤24h → 1h，7d → 1h
- `level_distribution` 统计各 level 日志条数
- `top_services_by_volume` 取前 10
- `top_errors` 取前 5
- `silent_services` = 注册了但 `last_log_at < now() - 5min`
- 此接口数据量大，考虑在 Python 分析模块定时预聚合到 PostgreSQL，API 读缓存

### 前端注意事项

- `log_volume_trend` 画面积图（总量）+ 折线图（错误量）叠加
- `level_distribution` 画水平条形图，DEBUG/INFO 灰色，ERROR 红色
- `top_services_by_volume` 画水平条形图
- 页面顶部放 4 个指标卡片：total_logs / error_rate / active_services / silent_services
