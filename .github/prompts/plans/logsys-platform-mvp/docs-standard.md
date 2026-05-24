> plan: logsys-platform-mvp
> phase: docs-standard
> status: confirmed

# 项目文档体系规范

## 文档金字塔

```
        ┌──────────────┐
        │  README.md   │  ← 项目入口，5 分钟了解全貌
        └──────┬───────┘
               │
    ┌──────────┼──────────┐
    │          │          │
    ▼          ▼          ▼
┌───────┐ ┌───────┐ ┌──────────┐
│focus- │ │api-   │ │project-  │
│spec   │ │spec   │ │spec      │
│需求契约│ │接口契约│ │项目规范   │
└───┬───┘ └───┬───┘ └────┬─────┘
    │         │           │
    └─────────┼───────────┘
              ▼
    ┌─────────────────┐
    │  执行层文档       │
    │ change-request   │ ← 需求变更
    │ review-protocol  │ ← 代码审核
    │ dev-workflow     │ ← 开发流程
    │ adr/             │ ← 架构决策记录
    └─────────────────┘
```

## 文档清单（8 份）

| # | 文档 | 路径 | 负责人 | 何时写 | 何时更新 |
|---|------|------|--------|--------|----------|
| 1 | **README.md** | 项目根目录 | 架构师 | Phase 1 结束 | 每次 release |
| 2 | **focus-spec.md** | `.github/prompts/` | 需求分析师 | 需求阶段 | 需求变更时重置 |
| 3 | **api-spec.md** | `.github/prompts/plans/<task>/` | 架构师 | 设计阶段 | API 变更时 |
| 4 | **schema.sql** | `.github/prompts/plans/<task>/` | 架构师 | 设计阶段 | DDL 变更时 |
| 5 | **project-spec.md** | `.github/prompts/` | 架构师 | 设计阶段 | 技术选型变更时 |
| 6 | **development-workflow.md** | `.github/prompts/plans/<task>/` | 架构师 | 开发前 | 流程调整时 |
| 7 | **change-request.md** | `.github/prompts/plans/<task>/` | 提出人 | 需求变更时 | 每次变更新增条目 |
| 8 | **review-protocol.md** | `.github/prompts/plans/<task>/` | 架构师 | 开发前 | 审核标准调整时 |

## 各文档职责

### 1. README.md

**一句话**：路人 5 分钟知道这是什么、怎么跑起来。

**必须包含**：
- 项目简介（1 段）
- 快速开始（`docker compose up -d`）
- 架构图（ASCII 或图片）
- 文档索引（指向本体系的其他文档）
- 技术栈一览

**禁止**：
- 长篇大论的背景故事
- 不完整的配置步骤（必须从零到跑通）

---

### 2. focus-spec.md（需求契约）

**一句话**：锁死做什么、不做什么、怎么验收。

**强制 4 章结构**（已实现）：
1. 场景还原
2. 核心业务边界（IN / OUT）
3. 禁止触碰黑名单
4. 核心测试断言清单

**生命周期**：
- 需求确认 → 签字 → `status: confirmed`
- 需求变更 → 询问是否重置
- 任务完成并 git commit → 自动过期
- `/clear` 后 → 自动过期

---

### 3. api-spec.md（接口契约）

**一句话**：前后端的法律合同。所有接口的精确定义。

**每个接口必须包含**：

```markdown
### POST /api/v1/logs/query

**用途**：按条件查询日志

**请求体**：
| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| service_name | string | 否 | — | 不传=全部服务 |
| level | string | 否 | — | TRACE/DEBUG/INFO/WARN/ERROR/FATAL |
| keyword | string | 否 | — | 全文搜索 message 字段 |
| trace_id | string | 否 | — | 精确匹配 |
| start_time | string | 是 | — | ISO 8601，如 2026-05-24T00:00:00Z |
| end_time | string | 是 | — | ISO 8601 |
| page | number | 否 | 1 | ≥1 |
| page_size | number | 否 | 50 | 1-200 |
| sort | string | 否 | desc | asc / desc |

**响应 200**：
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
      "level": "ERROR",
      "logger": "com.example.OrderService",
      "message": "Failed to create order",
      "trace_id": "a1b2c3d4e5f67890",
      "span_id": "1234567890abcdef",
      "exception_type": "java.lang.IllegalStateException",
      "exception_message": "insufficient stock",
      "exception_stacktrace": "...",
      "tags": { "order_id": "ORD-123" },
      "fields": { "duration_ms": 1450 },
      "source_host": "node-1",
      "source_type": "stdout"
    }
  ]
}
```

**响应 400**：
```json
{
  "code": "BAD_REQUEST",
  "message": "start_time and end_time are required",
  "details": null
}
```

**后端实现要求**：
- start_time / end_time 必需，缺一返回 400
- end_time - start_time ≤ 7 天，超出返回 400
- page_size ≤ 200，超出截断为 200
- keyword 走 ClickHouse tokenbf_v1 索引
- 无结果返回 `{ total: 0, items: [] }`，状态码 200

**前端注意事项**：
- items 可能为空数组
- exception_* 字段可能为 null（非 ERROR 日志无异常信息）
- tags 和 fields 可能为空对象
- page 从 1 开始，不是 0
```

**规则**：
- 每个接口写清楚：用途、请求体/参数（含类型、必填、默认值）、正常响应、异常响应、后端实现要求、前端注意事项
- 字段名用 snake_case（与 ClickHouse 列名一致，减少转换）
- 时间统一 ISO 8601 UTC
- 分页统一 `{ total, page, page_size, items }`
- 错误响应统一 `{ code, message, details }`

---

### 4. schema.sql（数据契约）

**一句话**：所有表结构、索引、约束的唯一定义源。

**规则**：
- 分两个文件：`schema.clickhouse.sql` 和 `schema.postgres.sql`
- 每个字段必须有 `COMMENT`
- 索引必须有注释说明加速哪种查询
- 禁止 ON DELETE CASCADE（防止误删）
- ClickHouse 表必须指定 TTL 策略

---

### 5. project-spec.md（项目规范）

**一句话**：项目的技术选型、命名规范、目录约定、环境配置。

**必须包含**：
- 技术栈与版本号（精确到 major.minor）
- 项目目录结构（每个目录一句话说明用途）
- 命名规范（包名、类名、表名、API 路径、Git 分支）
- 环境变量清单
- 端口分配表

---

### 6. development-workflow.md（开发流程）

**已写入**，覆盖：角色分工、分支策略、开发顺序、Mock 规范、协作流程、PR 规则。

---

### 7. change-request.md（需求变更记录）

**一句话**：需求不可能冻结。变更必须留痕，禁止口头改需求。

**格式**：

```markdown
> change-id: CR-001
> created: 2026-05-26T10:00:00+08:00
> proposer: 架构师
> status: proposed | accepted | rejected
> affects: api-spec.md, schema.sql

## 变更描述
[一句话描述要改什么]

## 变更原因
[为什么需要改，不改会怎样]

## 影响范围
- 需修改的文档：[列出]
- 需修改的代码模块：[列出]
- 是否影响已有 API：[是/否]

## 决策
[接受/拒绝，理由]
```

---

### 8. review-protocol.md（代码审核协议）

**一句话**：架构师审核 PR 的标准化流程和判定标准。

**审核等级**：

| 等级 | 触发条件 | 处理方式 |
|------|----------|----------|
| **P0 阻塞** | 安全漏洞、数据丢失风险、API 契约破坏 | 必须修复，禁止合并 |
| **P1 重要** | 架构漂移、性能隐患、测试缺失 | 建议修复，架构师裁定 |
| **P2 建议** | 命名可读性、代码风格、重复代码 | 可选修复，不阻塞合并 |

**审核流程（必须按序）**：
```
1. CI 通过（编译 + 测试 + lint）
     ↓
2. 契约检查（API 响应与 api-spec.md 比对）
     ↓
3. 代码检查（按 review checklist）
     ↓
4. 功能验证（curl / .http 文件实测）
     ↓
5. Approve → 通知作者合并
```

---

## 文档变更联动规则

当一份文档变更时，必须检查并同步更新关联文档：

| 变更文档 | 联动检查 |
|----------|----------|
| focus-spec.md | api-spec.md → schema.sql → project-spec.md |
| api-spec.md | 前端 MSW mock → 后端 Controller → review checklist |
| schema.sql | api-spec.md 字段定义 → 后端 DAO 层 |
| project-spec.md | README.md |

**规则**：改 A 必须扫一遍 B/C/D，在 PR 描述中声明「已检查，无联动影响」或「同步更新了 B」。

---

## 下一步

本文档确认后，依次输出：
1. **api-spec.md** — 完整接口契约（6 个 API）
2. **schema.sql** — ClickHouse + PostgreSQL DDL
3. **project-spec.md** — 项目技术规范
4. **change-request.md** — 变更记录模板 + 第一条记录
5. **review-protocol.md** — 代码审核协议
