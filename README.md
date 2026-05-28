<p align="center">
  <img src="https://img.shields.io/badge/状态-MVP_开发中-6366f1?style=flat-square" />
  <img src="https://img.shields.io/badge/协议-Apache_2.0-success?style=flat-square" />
  <img src="https://img.shields.io/badge/内存-不到1GB-success?style=flat-square" />
  <img src="https://img.shields.io/badge/部署-一行命令-blue?style=flat-square" />
</p>

<h1 align="center">LogSys</h1>
<p align="center"><strong>一个不需要运维团队的日志平台。</strong></p>

---

## 这是什么

你有一台 2C4G 的云服务器，上面跑了五六个服务——Java、Python、Node.js 都有。日志到处乱飞：Docker stdout 里有，`/var/log` 里有，项目目录下还有。凌晨两点出了故障，你 SSH 上去，打开三个终端 `tail -f`，脑子里只有一个想法：

**"能不能跟大厂一样有个日志平台？——但我只有一台机器。"**

能。LogSys 就是干这个的。

```
docker compose up -d
```

一条命令，你的单机服务器就变成一个小型可观测性平台。

- **不改业务代码**。你现在的 logger 照常用，Vector 自动采集。
- **总内存占用不到 1GB**。跟你的业务服务跑在同一台机器上，不需要额外服务器。
- **Grafana 可视化开箱即用**。Phase 2 上专业 React 前端。

### 跟大厂方案比一比

| | ELK 全家桶 | LogSys |
|---|-----------|--------|
| 最低内存 | 4 GB | **512 MB** |
| 组件数 | 3 个重型守护进程 | **5 个轻量容器** |
| 磁盘开销 | 原始日志 2 倍体积 | **10 倍压缩（ClickHouse 列存）** |
| 部署时间 | 半天调 JVM 参数 | **`docker compose up -d`** |
| 查询引擎 | 全文索引，重 | **列存扫描 + 布隆过滤** |
| 跑在哪儿 | 独立集群 | **就你那台 20 块的 VPS** |

## 架构

```
  ┌──────────┐
  │ Java 应用 │── stdout ──┐
  └──────────┘            │
  ┌──────────┐            │     ┌──────────┐      ┌─────────────┐
  │Python 应用│── 文件 ────┼────▶│  VECTOR  │─────▶│  ClickHouse │
  └──────────┘            │     │ 日志采集  │      │  日志存储    │
  ┌──────────┐            │     │ 格式解析  │      └──────┬──────┘
  │ Node 应用 │── stdout ──┘     │ 路由分发  │             │
  └──────────┘                  └──────────┘      ┌──────┴──────┐
                                                   │ Spring Boot │
  ┌──────────┐                  ┌──────────┐      │  查询 API   │
  │ Grafana  │◀─────────────────│PostgreSQL│      └──────┬──────┘
  │ 可视化   │                  │  元数据   │             │
  └──────────┘                  └──────────┘      ┌──────┴──────┘
                                                   │   Python    │
  ┌──────────┐                                    │  分析引擎   │
  │React 前端│  (Phase 2)                         │  错误聚类   │
  │   SPA    │                                    └─────────────┘
  └──────────┘
```

## 快速开始

```bash
git clone git@github.com:thana0623/LogSystem.git
cd LogSystem
cp .env.example .env      # 改一下密码
docker compose up -d       # 完事
```

| 地址 | 是什么 |
|------|--------|
| `http://localhost:3000` | Grafana 仪表盘 |
| `http://localhost:8080/health` | 健康检查 |
| `http://localhost:8080/api/v1/logs/query` | 日志查询 API |

### 你的应用不需要知道 LogSys 的存在

只要你的日志是 JSON 格式打到 stdout，Vector 自动采集，零配置：

```json
{"timestamp":"2026-05-24T12:00:00Z","level":"ERROR","message":"数据库连接超时","service_name":"order-api","trace_id":"a1b2..."}
```

不是 JSON？Vector 的 regex 解析器内置了 Logback、Python logging、Winston 的格式模板，挂载日志目录就能采。

## 用 curl 试试

```bash
# 查最近 1 小时所有服务的 ERROR 日志
curl -X POST http://localhost:8080/api/v1/logs/query \
  -H "Content-Type: application/json" \
  -d '{"level":"ERROR","start_time":"2026-05-24T11:00:00Z","end_time":"2026-05-24T12:00:00Z","page":1,"page_size":20}'

# 今天 Top 10 错误
curl http://localhost:8080/api/v1/errors/top?range=24h

# 所有服务健康概览
curl http://localhost:8080/api/v1/services
```

## 功能规划

| 能力 | Phase 1 | Phase 2 |
|------|:---:|:---:|
| 统一日志采集（stdout + 文件） | ✅ | ✅ |
| 多语言兼容（Java / Python / Node.js） | ✅ | ✅ |
| 结构化存储（ClickHouse，30 天自动清理） | ✅ | ✅ |
| 日志查询 API — 筛选、分页、排序 | ✅ | ✅ |
| Grafana 预置仪表盘 ×2 | ✅ | ✅ |
| 错误聚类 + Top-N 排行榜 | — | 🚧 |
| 服务健康评分 + 趋势统计 | — | 🚧 |
| 专业 React 前端（4 页面） | — | 🚧 |
| AI 日报 + 根因分析（Phase 3） | — | 💡 |

## 技术选型

| 层 | 选型 | 理由 |
|----|------|------|
| **采集** | [Vector](https://vector.dev) | Rust 写的，128MB 内存，TOML 配置。比 Fluent Bit 干净。 |
| **存储** | [ClickHouse](https://clickhouse.com) | 列存，10:1 压缩。同样数据量，内存只有 ES 的四分之一。 |
| **API** | Spring Boot 3.3 / Java 17 | 工业级稳定性，团队没有学习成本。 |
| **元数据** | PostgreSQL 16 Alpine | 轻得几乎不占资源。 |
| **可视化** | Grafana 11 OSS | MVP 阶段零前端成本。 |
| **分析** | Python 3.12 | 错误聚类、统计聚合。Phase 2。 |
| **前端** | React 18 + shadcn/ui + Tailwind | Phase 2。Linear / Datadog 级别的 UI 质感。 |
| **部署** | Docker Compose | 一个文件、一条命令、一台机器。 |

## 辅助工具

### CLI 日志管理 (`tools/cli/logsystem.py`)

一个本地命令行日志工具，用于个人笔记式日志管理（与平台的日志采集系统独立）。

要求 Python 3.10+。

```bash
# 新增日志
python tools/cli/logsystem.py add --title "今天总结" --content "完成了日志系统原型" --tags work,python

# 查看所有日志
python tools/cli/logsystem.py list

# 按关键字过滤
python tools/cli/logsystem.py list --keyword 原型

# 按标签过滤
python tools/cli/logsystem.py list --tag work

# 删除日志
python tools/cli/logsystem.py delete --log-id 1
```

默认数据文件：`logs.json`（位于当前目录，可通过 `--file` 指定其他路径）。

## 项目文档

| 文档 | 内容 |
|------|------|
| [focus-spec.md](.github/prompts/focus-spec.md) | 需求契约 — 场景、边界、黑名单、验收断言 |
| [api-spec.md](.github/prompts/plans/logsys-platform-mvp/api-spec.md) | 8 个 API 完整契约 — 请求/响应/错误/后端实现要求/前端注意事项 |
| [schema.sql](.github/prompts/plans/logsys-platform-mvp/schema.sql) | 数据库 DDL — 建表、索引、TTL、设计决策说明 |
| [project-spec.md](.github/prompts/plans/logsys-platform-mvp/project-spec.md) | 技术栈版本、命名规范、目录结构、端口分配 |
| [development-workflow.md](.github/prompts/plans/logsys-platform-mvp/development-workflow.md) | 开发流程 — 分支策略、Mock 规范、PR 规则 |
| [review-protocol.md](.github/prompts/plans/logsys-platform-mvp/review-protocol.md) | P0/P1/P2 三级代码审核清单 |
| [change-request.md](.github/prompts/plans/logsys-platform-mvp/change-request.md) | 需求变更模板 — 口头需求不算数 |

## 参与开发

**后端（Java）** — 先读 [api-spec.md](.github/prompts/plans/logsys-platform-mvp/api-spec.md)。Controller → Service → DAO 三层。DTO 和 VO 必须分离。每个接口带 `.http` 测试文件。提 PR 前对着 [review-protocol.md](.github/prompts/plans/logsys-platform-mvp/review-protocol.md) 自查。

**前端（React）** — MSW Mock 先行。不启动后端也能完整开发全部页面。切一个环境变量接真实 API。UI 规范在 [focus-spec.md §8](.github/prompts/focus-spec.md)。

**Python（分析引擎）** — 脚本必须幂等可重复执行。配置全走环境变量。归一化算法必须有单测。

## License

Apache 2.0 — 拿去用，拿去改，拿去发。

---

<p align="center">
  <sub>为实验室、学生、小团队而建。<br/>可观测性，不需要企业级的复杂度税。</sub>
</p>
