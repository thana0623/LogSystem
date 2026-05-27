> plan: logsys-platform-mvp
> phase: review-protocol
> status: confirmed
> base: docs-standard.md §8, development-workflow.md §7

# 代码审核协议

## 1. 审核等级

| 等级 | 标签 | 定义 | 处理 |
|------|------|------|------|
| **P0** | `[P0 阻塞]` | 安全漏洞、数据丢失风险、API 契约破坏、生产不可用 | **必须修复**，禁止合并 |
| **P1** | `[P1 重要]` | 架构漂移、性能隐患、测试缺失、错误处理不当 | 建议修复，架构师最终裁定是否阻塞 |
| **P2** | `[P2 建议]` | 命名可读性、代码风格、重复代码、注释不足 | 可选修复，记入 tech-debt 但不阻塞合并 |

## 2. 审核流程

```
PR 提交
  │
  ├─ 1. CI 自动检查通过
  │     ├─ 编译通过
  │     ├─ 单元测试通过
  │     └─ Lint 通过（Checkstyle / ESLint）
  │
  ├─ 2. 契约检查（架构师）
  │     ├─ 响应体与 api-spec.md 比对
  │     ├─ 字段名、类型、嵌套层级一致
  │     ├─ 分页结构 = { total, page, page_size, items }
  │     ├─ 错误格式 = { code, message, details }
  │     └─ 时间格式 = ISO 8601 UTC
  │
  ├─ 3. 代码检查（架构师）
  │     ├─ 按模块 checklist 逐条过
  │     └─ 标注 P0 / P1 / P2
  │
  ├─ 4. 功能验证（架构师）
  │     ├─ curl 或 .http 文件实测
  │     ├─ 正常场景
  │     ├─ 边界场景（空结果、单条、最大值）
  │     └─ 错误场景（缺参数、超范围、下游挂了）
  │
  └─ 5. 合并决策
        ├─ P0 = 0 → Approve ✅
        ├─ P0 > 0 → Request Changes ❌
        └─ P1 > 0 → 架构师裁定
```

## 3. 各模块审核清单

### 3.1 后端 Java（Spring Boot）

#### 层级检查

- [ ] **Controller**
  - [ ] 方法签名与 api-spec.md 完全一致（路径、Method、参数名、参数类型）
  - [ ] 只做三件事：接收请求 → 调 Service → 返回 ApiResponse
  - [ ] 没有直接调 Mapper（禁止跨层）
  - [ ] 没有拼接 SQL 字符串
  - [ ] 入参有 `@Valid` 校验
  - [ ] 返回值统一用 `ApiResponse<T>` 包装

- [ ] **Service**
  - [ ] 不碰 `HttpServletRequest` / `HttpServletResponse`
  - [ ] 只处理 POJO
  - [ ] 有 `@Transactional` 的地方确实是事务边界
  - [ ] 异常抛出用自定义业务异常，不抛裸 `RuntimeException`

- [ ] **DAO（Mapper）**
  - [ ] 只写 SQL，不写业务逻辑
  - [ ] ClickHouse SQL 显式列出列名（禁止 `SELECT *`）
  - [ ] 排序参数校验白名单后再用 `${}` 传入
  - [ ] 分页参数 `page` 和 `pageSize` 有默认值兜底

- [ ] **Model**
  - [ ] DTO（入参）和 VO（出参）已分离
  - [ ] 没有用同一个类同时做入参和出参
  - [ ] 没有返回 `Map<String, Object>` 或裸 `Object`
  - [ ] VO 字段名与 api-spec.md 响应体 JSON key 一一对应

- [ ] **Config**
  - [ ] 数据源配置从 `application.yml` 读环境变量，无硬编码
  - [ ] CORS 配置仅允许前端域名
  - [ ] Jackson 配置：日期格式 `yyyy-MM-dd'T'HH:mm:ss.SSSX`，时区 UTC

#### 安全与性能

- [ ] 日志查询的时间范围有上限校验（≤ 7 天）
- [ ] pageSize 有上限校验（≤ 200）
- [ ] ClickHouse 查询必有 `timestamp BETWEEN ? AND ?`，禁止全表扫描
- [ ] 没有 SQL 注入风险（用户输入不进 `${}`）
- [ ] 没有 `System.out.println` / `e.printStackTrace()` 残留
- [ ] 日志用 SLF4J，不是 `System.out`

#### 测试

- [ ] Controller 有 MockMvc 集成测试
- [ ] 测试覆盖：正常返回 200、空结果 200、缺参数 400、下游挂 503
- [ ] `.http` 文件或 curl 脚本存在且全部通过

---

### 3.2 前端 Next.js（Phase 2）

#### 结构检查

- [ ] API 调用统一走 `src/shared/lib/api-client.ts`，无裸 `fetch` / `axios`
- [ ] 组件不包含 API 调用逻辑（调 hooks，不直接调 client）
- [ ] 组件不包含业务逻辑（业务在 `features/`）
- [ ] 页面在 `src/app/`（App Router 文件系统路由），通用组件在 `components/`

#### Mock 检查

- [ ] MSW handlers 返回的数据结构与 api-spec.md 一致
- [ ] Mock 覆盖：正常数据（≥20 条）、空列表、单条数据、超长文本（500+ 字符）、特殊字符
- [ ] `NEXT_PUBLIC_ENABLE_MOCK=true` 时页面可完整交互，无需后端

#### 状态覆盖（每一处数据请求）

- [ ] **Loading 态** — 有骨架屏或 spinner，不是白屏
- [ ] **Empty 态** — 有 "暂无数据" 提示，不是空白
- [ ] **Error 态** — 有错误提示 + 重试按钮
- [ ] **Edge 态** — 单条数据布局不崩、超长文本截断、null 字段不显示 `null` 字符串

#### UI 规范

- [ ] 所有日志和指标列使用等宽字体（JetBrains Mono）
- [ ] 颜色只用设计 Token（CSS 变量），无 hardcode `#ff0000`
- [ ] 间距只用 Tailwind Token，无 magic number
- [ ] 圆角仅 `rounded-md` / `rounded-lg`
- [ ] 暗色模式为默认
- [ ] 动画 ≤ 150ms，仅 opacity / translateY
- [ ] 表格紧凑行高，粘性表头，无斑马纹
- [ ] 没有 `console.log` 残留
- [ ] 没有 `any` 类型
- [ ] 没有 `rounded-3xl` / `rounded-full` / `shadow-2xl`

---

### 3.3 Python 分析模块（Phase 2）

- [ ] 数据库连接参数从环境变量读取
- [ ] 脚本可重复执行（幂等），重复跑不会产生重复数据
- [ ] 异常签名归一化函数有单元测试
- [ ] 归一化规则有注释说明（为什么替换这个模式）
- [ ] 定时调度配置清晰（间隔、超时、失败重试）
- [ ] 没有硬编码文件路径
- [ ] 日志用 `logging` 模块，不是 `print()`

---

### 3.4 基础设施 / 配置

- [ ] Dockerfile 使用多阶段构建（如有 JDK）
- [ ] Dockerfile 基础镜像用 `alpine` 变体
- [ ] `.env` 不在 git 跟踪中
- [ ] `.env.example` 存在且值全部为 `changeme`
- [ ] 端口映射无冲突
- [ ] 健康检查命令正确
- [ ] `depends_on` 条件正确

---

## 4. 审核输出格式

架构师审核完成后，在 PR 下留评论：

```markdown
## Code Review — {PR 标题}

### 契约检查
- [x] API 响应体与 api-spec.md 一致
- [x] 分页结构正确
- [x] 时间格式 ISO 8601 UTC

### 发现问题

| 级别 | 文件 | 行号 | 问题描述 |
|------|------|------|----------|
| P0   | LogQueryController.java | L42 | start_time 未校验非空 |
| P1   | LogQueryService.java | L78 | 排序字段未校验白名单 |
| P2   | LogEntryVO.java | L15 | exceptionMessage 建议改为 exception_message 保持与 DB 一致 |

### 决策
- [ ] Request Changes — 存在 P0 问题
- [ ] Approve — P0 已清零，P1 可后续处理
- [ ] Approve with Comments — 无阻塞问题
```

## 5. Tech Debt 记录

P2 级别问题统一记入 Git Issue，标签 `tech-debt`：

```markdown
## [tech-debt] {简短描述}

**发现于**：PR #{编号}
**文件**：{路径}:{行号}
**问题**：{描述}
**建议**：{改进方向}
**严重程度**：低
```

架构师每 2 周扫一次 `tech-debt` Issue，批量评估是否修复。

## 6. 合并后验证

PR 合并后，架构师在本地 `develop` 分支执行：

```bash
docker compose up -d --build   # 重建受影响容器
# 验证 .http 文件全部通过
# 验证前端页面正常渲染（如涉及前端）
```
