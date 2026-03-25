# P0 MVP前后端开发与验证实施方案（现状对齐版）

> 版本：v2026-03-24  
> 说明：本文件按当前代码实现对齐，不再使用“纯计划态”描述。

## 1. 文档目标
1. 给出当前 MVP 前后端“已实现能力”的统一说明。
2. 标注已落地、部分落地、未落地项，避免口径歧义。
3. 作为 `doc/MVP` 与代码联调、验收、交付的基准说明。

## 2. 对齐范围与冲突规则
1. 代码对齐范围：
   - 后端：`code/backend`
   - 前端：`code/frontend`
   - 脚本：`run_stack.ps1`、`code/scripts/*`
   - CI：`.github/workflows/p0-quality-gate.yml`、`.github/workflows/backend-benchmark-gate.yml`
2. 冲突处理：
   - 本文与代码冲突时，以代码为准。
   - 本文与其他文档冲突时，仍遵循 `doc/00_现行口径基线_2026-03-21.md` 的优先级规则。

## 3. 当前后端实现（`code/backend`）

### 3.1 技术与运行
1. 技术栈：`Java 21 + Spring Boot 3.4.x + JPA + Flyway + PostgreSQL/H2 + Apache POI + sqlite-jdbc`。
2. 默认 profile：`local`（H2，`application-local.yml`）。
3. PostgreSQL 模式可用（`application.yml` + `code/scripts/run_backend_pg.ps1`）。
4. 时区配置：`Asia/Shanghai`。

### 3.2 路由分层
1. 外部契约：`/v1/*`（ERP/MES/报表/回写）。
2. 内部契约：`/internal/v1/internal/*`（待排池、版本、指令审批、预警、仿真、审计、同步监控）。
3. 兼容路由：`/api/*`（历史 MVP 调用路径仍保留）。

### 3.3 排程核心（当前代码行为）
1. `generateSchedule` 已支持 `base_version_no` 作为输入，并将锁单订单作为“保留基线”处理。
2. 锁单保留不依赖 `autoReplan=true`，当前逻辑统一保留锁单语义。
3. 未排任务已输出结构化原因（含 `reason_code` / `dependency_status` / `task_status` 等）。
4. 原因码包含：`CAPACITY_MANPOWER`、`CAPACITY_MACHINE`、`CAPACITY_UNKNOWN`、`MATERIAL_SHORTAGE`、`DEPENDENCY_BLOCKED`、`FROZEN_BY_POLICY`、`LOCKED_PRESERVED`。
5. 发布/回滚支持幂等（`request_id + action` 账本）。

### 3.4 数据与配置能力
1. 内置主数据在线配置：
   - `GET /internal/v1/internal/masterdata/config`
   - `POST /internal/v1/internal/masterdata/config`
2. 调度负荷视图接口：
   - `GET /internal/v1/internal/schedule-versions/{versionNo}/daily-process-load`
   - `GET /internal/v1/internal/schedule-versions/{versionNo}/shift-process-load`
3. 算法解释接口：
   - `GET /internal/v1/internal/schedule-versions/{versionNo}/algorithm`
4. ERP 真实订单读取：
   - 按 `mvp.erp.sqlite-path` 读取 SQLite 订单；
   - 读取失败时回退到内置种子数据。

### 3.5 安全与协议
1. 契约路由（`/v1`、`/internal/v1`）要求 `Authorization: Bearer ...`。
2. 写接口要求 `request_id`（Header/Body/Query 解析）。
3. 响应统一回传 `x-request-id`。
4. CORS 默认放行 `http://localhost:5932`。

## 4. 当前前端实现（`code/frontend`）

### 4.1 已实现路由
1. `/dashboard`
2. `/orders/pool`
3. `/schedule/board`
4. `/reports/plans`
5. `/schedule/versions`
6. `/dispatch/commands`
7. `/execution/wip`
8. `/alerts`
9. `/audit/logs`
10. `/masterdata`
11. `/ops/integration`
12. `/simulation`
13. `/guide`

### 4.2 关键页面对齐点
1. 调度台（`/schedule/board`）已接入：
   - 草稿生成与发布；
   - 版本任务明细；
   - 每天工序负荷；
   - 分配解释（瓶颈原因/维度/说明）。
2. 版本中心（`/schedule/versions`）已接入：
   - 差异对比；
   - 发布/回滚；
   - 算法解释与未排原因摘要。
3. 主数据页（`/masterdata`）已接入可编辑参数并落地保存到后端配置接口。
4. 报表页（`/reports/plans`）已接入版本选择、导出、周/月共享字段一致性检查。
5. 仿真页（`/simulation`）当前默认仅开放“手动模拟”入口；批量仿真入口保留代码但 UI 开关关闭。

## 5. 运行与联调入口
1. 仓库根一键启动：`.\run_stack.ps1` / `.\run_stack.bat`
2. 代码目录脚本：
   - `code/scripts/run_backend_local.ps1`
   - `code/scripts/run_backend_pg.ps1`
   - `code/scripts/run_frontend.ps1`
   - `code/scripts/run_stack.ps1`
3. 默认地址：
   - Frontend：`http://localhost:5932`
   - Backend：`http://localhost:5931`

## 6. 测试与质量门禁（现状）

### 6.1 后端测试
1. `code/backend/src/test/java/com/autoproduction/mvp/MvpApiTest.java`：
   - 覆盖外部/内部契约主要路径；
   - 覆盖周/月报表导出与金标准模板结构对齐校验；
   - 覆盖重排、指令审批、仿真关键链路。
2. `code/backend/src/test/java/com/autoproduction/mvp/core/SchedulerBenchmarkSmokeTest.java`：
   - 支持 100/1000/5000 单规模基准；
   - 支持阈值参数化（`-Dmvp.benchmark.thresholds=...`）。

### 6.2 前端测试
1. `code/frontend/src/App.test.jsx`：路由壳与导航基础校验。
2. `code/frontend/src/pages/SimulationPage.test.jsx`：手动模拟接口调用与批量入口隐藏校验。

### 6.3 CI 门禁
1. 工件门禁：`.github/workflows/p0-quality-gate.yml`
2. 后端性能门禁：`.github/workflows/backend-benchmark-gate.yml`

## 7. 本次与旧版文档的修正点
1. 将“计划态任务拆解”改为“代码现状说明 + 落地状态”。
2. 补充代码已实现但旧文档遗漏的接口：
   - `daily-process-load`
   - `shift-process-load`
   - `algorithm`
   - `masterdata/config`
3. 修正仿真能力描述为“手动模拟默认启用，批量仿真入口关闭”。
4. 补充后端基准测试与 benchmark workflow。

## 8. 待落地项（保持透明）
1. 独立 worker/sync 进程未启用，当前仍为单体内实现。
2. RabbitMQ 未启用（P1 规划项）。
3. 批量仿真 UI 入口暂未开放（代码保留，待开关策略确认）。

## 9. 当前验收基线（MVP）
1. 关键链路可闭环：待排池 -> 调度台 -> 版本发布 -> 报工 -> 预警/重排。
2. 周/月计划接口与导出可用，模板结构与金标准一致。
3. 排产输出具备可解释性字段（任务状态、依赖状态、未排原因）。
4. 请求链路可追踪：`request_id` 在前后端与接口响应中贯通。

## 10. 关联文档
1. `doc/MVP/02_金标准报表数据层对齐_周计划与月计划.md`
2. `doc/MVP/refactor/01_锁单基线与冻结语义重构方案.md`
3. `doc/MVP/refactor/02_排程引擎可解释性与约束建模重构方案.md`
4. `doc/MVP/refactor/03_性能可观测性与测试体系重构方案.md`
