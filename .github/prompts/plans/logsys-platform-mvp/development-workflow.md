> plan: logsys-platform-mvp
> phase: workflow
> status: confirmed

# 开发流程与协作规范

## 1. 角色分工

| 角色 | 人员 | 职责 |
|------|------|------|
| 架构师 | 你 | 架构设计、API 契约、DDL 审核、前后端联调、代码审核、PR 合并 |
| 后端（Java） | 你 + 待定 | Spring Boot API、ClickHouse 查询、Vector 配置 |
| 后端（Python） | 待定（外援） | 错误聚类脚本、统计分析、AI 扩展 |
| 前端（Next.js） | 待定（专业前端） | Next.js 页面、图表、UI 组件 |

## 2. 分支策略

```
main          ← 永远可部署，只接受 PR 合并
  │
  ├─ develop  ← 集成分支，功能分支合并到这里验证
  │
  ├─ feature/api-log-query    ← 后端：日志查询 API
  ├─ feature/api-error-cluster ← 后端：错误聚类 API
  ├─ feature/cluster-engine   ← Python：聚类引擎
  ├─ feature/ui-dashboard     ← 前端：Dashboard 页面
  ├─ feature/ui-log-explorer  ← 前端：Log Explorer 页面
  └─ ...
```

**规则**：
- 一个分支只做一件事，分支名用 kebab-case
- 前端分支以 `feature/ui-` 开头，后端以 `feature/api-` 开头，Python 以 `feature/cluster-` 开头
- 禁止直接在 `main` 或 `develop` 上 commit
- PR 标题格式：`[模块] 简短描述`，如 `[API] 实现日志查询接口`、`[UI] Log Explorer 页面`

## 3. 开发顺序（Phase 1 — MVP）

```
第 1 步：基础设施
  ├── Docker Compose 骨架（vector + clickhouse + postgres + grafana）
  ├── ClickHouse DDL（logs 表 + 索引）
  ├── PostgreSQL DDL（services 表）
  └── Vector 基础配置（采集 docker stdout → ClickHouse）
  产出：docker compose up 后日志能进 ClickHouse

第 2 步：后端 API 核心
  ├── Spring Boot 项目骨架
  ├── POST /api/v1/logs/query  ← 最优先，前端所有页面依赖
  ├── GET  /api/v1/services
  └── Swagger 文档自动生成
  产出：前端可以调 API 拿到真实数据格式

第 3 步：前端启动（与第 2 步并行准备工作）
  ├── Next.js 项目骨架 + App Router + 布局
  ├── Mock Server 搭建（MSW）
  ├── 全局样式 + 设计 Token
  └── 共享组件库（Button/Input/Select/Table/Card/Badge/Toast）
  产出：前端项目能跑，路由通，组件就绪

第 4 步：前端页面开发
  ├── Overview Dashboard
  ├── Log Explorer
  ├── Error Cluster Page（先读 mock 数据）
  └── Service Detail Page
  产出：4 个页面全部可交互，使用 mock 数据

第 5 步：前后端联调
  ├── 前端切到真实 API，逐个页面联调
  ├── 修复字段名不匹配、类型不一致
  ├── 确认分页、排序、筛选行为
  └── 确认错误状态处理（500 / 超时 / 空数据）
  产出：4 个页面全部通过真实数据验证

第 6 步：Python 分析模块
  ├── 错误签名提取 + 归一化
  ├── 定时写入 error_clusters 表
  ├── GET /api/v1/errors/top
  └── GET /api/v1/stats/overview
  产出：聚类结果可查，前端 Error 页面切真数据

第 7 步：收尾
  ├── Grafana 预置仪表盘
  ├── README 部署文档
  └── Docker Compose 最终验证
```

## 4. 前端 Mock 数据规范（硬性要求）

### 4.1 Mock 方案：MSW（Mock Service Worker）

前端**禁止**在组件内写 `if (USE_MOCK)` 分支、禁止写临时 JSON 文件、禁止用 `setTimeout` 模拟请求。

统一使用 **MSW**（Mock Service Worker），拦截网络请求，返回符合 API 契约的假数据。

### 4.2 Mock 数据质量要求

- 每条 mock 数据必须**与 api-spec.md 定义的响应结构完全一致**（字段名、类型、嵌套层级）
- 列表接口至少返回 **20 条** mock 数据，覆盖正常 + 异常场景
- 必须包含以下边界场景的 mock：

| 场景 | 说明 |
|------|------|
| 正常数据 | 各字段合法值 |
| 空列表 | `total: 0, items: []` |
| 单条数据 | 仅 1 条，验证布局不崩 |
| 超长文本 | message 字段 500+ 字符，验证截断/换行 |
| 特殊字符 | 日志包含 `<script>`、SQL 注入字符串、Unicode emoji |
| 全部 ERROR | 验证错误页面的红色标记 |
| 跨天数据 | timestamp 跨越 00:00，验证时间轴 |

### 4.3 Mock 文件结构

```
logsys-ui/src/
├── mocks/
│   ├── server.ts           ← MSW server 配置
│   ├── handlers/
│   │   ├── logs.ts         ← /api/v1/logs/query handler
│   │   ├── errors.ts       ← /api/v1/errors/* handlers
│   │   ├── services.ts     ← /api/v1/services/* handlers
│   │   └── stats.ts        ← /api/v1/stats/* handlers
│   └── fixtures/
│       ├── logs.fixture.ts       ← 日志 mock 数据工厂
│       ├── errors.fixture.ts     ← 错误聚类 mock 数据工厂
│       ├── services.fixture.ts   ← 服务列表 mock 数据
│       └── stats.fixture.ts      ← 统计数据 mock
```

### 4.4 前端开发模式切换

```typescript
// src/shared/lib/api-client.ts
// 环境变量控制，构建时决定，禁止运行时判断

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? '';

// 开发模式：NEXT_PUBLIC_ENABLE_MOCK=true → MSW 拦截
// 联调模式：NEXT_PUBLIC_ENABLE_MOCK=false → 直连后端
// 所有 API 调用统一走这个 client，不得绕过
```

**验收标准**：前端开发者执行 `npm run dev` 即可在无后端情况下完成全部页面开发，页面功能完整可用。

## 5. 后端测试规范

### 5.1 API 必须通过 curl 或 http 文件验证

每个 API 开发完成后，必须提供一个 `.http` 文件或 curl 命令，架构师审核时直接执行验证：

```http
### 日志查询 — 正常场景
POST http://localhost:8080/api/v1/logs/query
Content-Type: application/json

{
  "service_name": "order-service",
  "level": "ERROR",
  "start_time": "2026-05-24T00:00:00Z",
  "end_time": "2026-05-24T23:59:59Z",
  "page": 1,
  "page_size": 20
}

### 日志查询 — 空结果
POST http://localhost:8080/api/v1/logs/query
Content-Type: application/json

{
  "service_name": "nonexistent",
  "start_time": "2026-05-24T00:00:00Z",
  "end_time": "2026-05-24T23:59:59Z"
}

### 日志查询 — 缺少必填字段（应返回 400）
POST http://localhost:8080/api/v1/logs/query
Content-Type: application/json

{
  "service_name": "order-service"
}
```

### 5.2 后端自测要求

- Controller 层写 Spring MockMvc 集成测试，覆盖正常 + 参数校验 + 空结果
- 禁止只测 Service 层，必须测到 HTTP 层（请求 → 响应完整链路）

## 6. 前后端协作流程

```
          架构师输出 api-spec.md
                  │
     ┌────────────┼────────────┐
     ▼            ▼            ▼
  后端开发     前端开发      架构师审核
     │            │            │
     │   Swagger  │   MSW      │
     │   文档生成  │   Mock     │
     │            │            │
     └────────────┼────────────┘
                  ▼
          前后端联调（架构师主持）
                  │
                  ▼
          PR → Code Review → Merge
```

**关键节点**：
1. `api-spec.md` 确认后，前后端**同时**开始，互不阻塞
2. 后端交付物：可调用的 API + Swagger 文档 + .http 测试文件
3. 前端交付物：MSW mock 驱动下功能完整的页面 + 组件
4. 联调时发现 API 契约问题，**先改 api-spec.md，再各自改代码**，不允许口头约定

## 7. 代码审核（Code Review）清单

每次 PR 审核，架构师逐条过：

### 后端 PR

- [ ] Controller 方法签名与 api-spec.md 一致（路径、方法、参数名）
- [ ] 响应体字段名与 api-spec.md 一致（包括嵌套层级）
- [ ] 分页返回结构为 `{ total, page, pageSize, items }`
- [ ] 时间字段格式为 ISO 8601（`2026-05-24T12:00:00.123Z`）
- [ ] 不允许返回 `Map<String, Object>` 或 `Object` 类型
- [ ] 错误响应有统一结构 `{ code, message, details }`
- [ ] ClickHouse 查询有时间范围限制（防止全表扫描）
- [ ] pageSize 有上限校验（不超过 200）
- [ ] .http 测试文件存在且通过
- [ ] 没有 System.out.println 残留

### 前端 PR

- [ ] MSW mock 数据结构与 api-spec.md 一致
- [ ] 组件不包含业务逻辑（只负责渲染 + 用户交互）
- [ ] API 调用统一走 `api-client.ts`，无裸 fetch/axios
- [ ] 没有 hardcode 的样式值（颜色/间距/字号一律走 Tailwind Token 或 CSS 变量）
- [ ] 所有列表有空状态处理（`items.length === 0`）
- [ ] 所有请求有 loading 状态处理
- [ ] 所有请求有 error 状态处理（含超时）
- [ ] 表格支持等宽字体（日志列）
- [ ] 没有 `console.log` 残留
- [ ] 没有 `any` 类型（TypeScript 严格模式）

### Python PR

- [ ] ClickHouse 连接参数从环境变量读取，无硬编码
- [ ] 脚本可重复执行（幂等）
- [ ] 异常签名归一化算法有单元测试
- [ ] 定时任务调度配置清晰

## 8. PR 合并规则

- 必须通过 Code Review 清单全部勾选
- 必须 rebase 到最新 `develop`，无冲突
- 合并方式：**Squash and Merge**，commit message 格式：
  ```
  [模块] 简短描述 (#PR编号)
  ```
- 谁开的 PR 谁负责点合并按钮（架构师审核通过后通知作者自行合并）

## 9. 环境变量规范

```
# .env（所有容器共享）

# ClickHouse
CLICKHOUSE_DB=logsys
CLICKHOUSE_USER=logsys
CLICKHOUSE_PASSWORD=<生成随机密码>

# PostgreSQL
POSTGRES_DB=logsys_meta
POSTGRES_USER=logsys
POSTGRES_PASSWORD=<生成随机密码>

# Spring Boot
LOG_LEVEL=INFO
SERVER_PORT=8080

# Grafana
GF_SECURITY_ADMIN_PASSWORD=<生成随机密码>

# 前端
NEXT_PUBLIC_API_BASE=http://localhost:8080
NEXT_PUBLIC_ENABLE_MOCK=true    # 开发=true, 联调=false, 生产=不存在此变量
```

`.env` 不入 git（已在 `.gitignore`），提供 `.env.example` 作为模板，值全部为 `changeme`。

## 10. 下一步

本文件确认后，下一步输出：
1. **schema.sql** — ClickHouse + PostgreSQL 完整 DDL
2. **api-spec.md** — 全部 REST API 契约（请求/响应/状态码/错误格式）
