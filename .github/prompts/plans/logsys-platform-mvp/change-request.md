> plan: logsys-platform-mvp
> phase: change-request
> status: confirmed
> base: docs-standard.md §7

# 需求变更记录

## 变更流程

```
提出人                   架构师                   开发
  │                       │                       │
  ├─ 写 CR 条目           │                       │
  │  (本文档末尾追加)      │                       │
  │                       │                       │
  └──────────────────────▶│                       │
                          │                       │
                          ├─ 评估影响范围          │
                          ├─ 标注 affects 文档     │
                          ├─ proposed/rejected     │
                          │                       │
                    ┌─────┴─────┐                 │
                    │ accepted   │ rejected        │
                    ▼           ▼                  │
              更新联动文档   通知提出人原因          │
              ├─ api-spec.md                      │
              ├─ schema.sql                       │
              └─ focus-spec.md (重置? y/n)        │
                    │                             │
                    └─────────────────────────────▶│
                                                  │
                                                  ├─ 改代码
                                                  └─ 提 PR
```

## 变更条目格式

每个变更条目按以下模板填写：

```markdown
---

## CR-{序号}: {一句话标题}

> change-id: CR-{序号}
> created: {ISO timestamp}
> proposer: {提出人角色}
> status: proposed | accepted | rejected
> affects: {逗号分隔的受影响文档}
> resolved_at: {决策时间，status 变更为 accepted/rejected 时填写}

### 变更描述
{一句话描述改什么}

### 变更原因
{为什么现在要改，不改的后果}

### 改动方案
{具体改什么，不是概念描述}

### 影响范围
- 需修改文档：{列出文件路径}
- 需修改代码：{列出模块}
- 是否破坏已有 API：是 / 否 {如果是，说明兼容方案}
- 是否需重置 focus-spec：是 / 否

### 决策
{accepted: 理由 | rejected: 理由}
```

## 变更条目

> 以下按时间倒序排列，最新变更在最上面。

---

## CR-001: 项目初始化基线

> change-id: CR-001
> created: 2026-05-24T20:30:00+08:00
> proposer: 架构师
> status: accepted
> affects: focus-spec.md, api-spec.md, schema.sql, project-spec.md, development-workflow.md, docs-standard.md
> resolved_at: 2026-05-24T20:30:00+08:00

### 变更描述
项目从零启动，建立完整文档体系和架构基线。

### 变更原因
新项目，无历史包袱。需要一次性地基打牢。

### 改动方案
1. 创建 focus-spec.md（需求契约）
2. 创建 docs-standard.md（文档体系规范）
3. 创建 api-spec.md（8 个 API 契约）
4. 创建 schema.sql（ClickHouse + PostgreSQL DDL）
5. 创建 project-spec.md（技术栈、命名、目录、端口规范）
6. 创建 development-workflow.md（开发流程、协作规范）
7. 创建 change-request.md（本文档）
8. 待创建 review-protocol.md（代码审核协议）

### 影响范围
- 需修改文档：全部新创建，无历史文档
- 需修改代码：无（尚未开始编码）
- 是否破坏已有 API：否
- 是否需重置 focus-spec：否

### 决策
accepted: 项目初始化基线，所有文档经架构师确认签字后生效。
