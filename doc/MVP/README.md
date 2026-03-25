# MVP 文档目录（现状对齐）

> 更新日期：2026-03-24  
> 对齐范围：`code/backend`、`code/frontend`、`.github/workflows`、`scripts/`

## 1. 核心文档
1. `01_P0_MVP前后端开发与验证实施方案.md`
2. `02_金标准报表数据层对齐_周计划与月计划.md`

## 2. 重构文档（状态更新）
1. `refactor/01_锁单基线与冻结语义重构方案.md`
2. `refactor/02_排程引擎可解释性与约束建模重构方案.md`
3. `refactor/03_性能可观测性与测试体系重构方案.md`

## 3. 当前代码实现摘要

### 3.1 后端（`code/backend`）
- 路由分层：
  - 外部契约：`/v1/*`
  - 内部契约：`/internal/v1/internal/*`
  - 兼容路由：`/api/*`
- 已实现的关键内部接口补充：
  - `GET /internal/v1/internal/schedule-versions/{versionNo}/daily-process-load`
  - `GET /internal/v1/internal/schedule-versions/{versionNo}/shift-process-load`
  - `GET /internal/v1/internal/schedule-versions/{versionNo}/algorithm`
  - `GET/POST /internal/v1/internal/masterdata/config`
- 报表接口支持 `version_no` 参数（周计划/月计划及导出）。
- 写接口要求 `request_id`，并返回 `x-request-id`。

### 3.2 前端（`code/frontend`）
- 已实现路由：
  - `/dashboard`
  - `/orders/pool`
  - `/schedule/board`
  - `/reports/plans`
  - `/schedule/versions`
  - `/dispatch/commands`
  - `/execution/wip`
  - `/alerts`
  - `/audit/logs`
  - `/masterdata`
  - `/ops/integration`
  - `/simulation`
  - `/guide`
- 当前仿真页面默认仅开放“手动模拟”（批量仿真代码保留，UI 开关关闭）。

## 4. 金标准来源（固定）
- `doc/offical_resource/2月计划（车间）(1).xls`
- `doc/offical_resource/周计划（3.16-3.22）(1).xlsx`

## 5. 报表接口
- 周计划数据：`GET /v1/reports/workshop-weekly-plan`
- 月计划数据：`GET /v1/reports/workshop-monthly-plan`
- 周计划导出（xlsx）：`GET /v1/reports/workshop-weekly-plan/export`
- 月计划导出（xls）：`GET /v1/reports/workshop-monthly-plan/export`

## 6. 运行口径
- 后端默认 profile：`local`（H2）。
- 一键启动：仓库根目录 `.\run_stack.ps1`（或 `.\run_stack.bat`）。
- 默认地址：前端 `http://localhost:5932`，后端 `http://localhost:5931`。
- 前端默认 API 基址：`http://localhost:5931`。

## 7. 质量门禁与测试
- 质量门禁（工件一致性）：`.github/workflows/p0-quality-gate.yml`
- 后端性能门禁（基准）：`.github/workflows/backend-benchmark-gate.yml`
- 本地入口：
  - `scripts/run_quality_gate.ps1`
  - `code/scripts/run_quality_gate.ps1`

## 8. 使用约束
- 涉及周计划/月计划字段改动，先更新 `02_金标准报表数据层对齐_周计划与月计划.md`。
- 文档与代码冲突时，以当前代码行为为准；再回写到 MVP 文档。
