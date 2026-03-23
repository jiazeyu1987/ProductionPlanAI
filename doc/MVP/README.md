# MVP 文档目录

1. `01_P0_MVP前后端开发与验证实施方案.md`  
2. `02_金标准报表数据层对齐_周计划与月计划.md`

## 金标准来源（固定）
- `doc/offical_resource/2月计划（车间）(1).xls`
- `doc/offical_resource/周计划（3.16-3.22）(1).xlsx`

## 报表接口
- 周计划数据：`GET /v1/reports/workshop-weekly-plan`
- 月计划数据：`GET /v1/reports/workshop-monthly-plan`
- 周计划导出（xlsx）：`GET /v1/reports/workshop-weekly-plan/export`
- 月计划导出（xls）：`GET /v1/reports/workshop-monthly-plan/export`
- 后端测试已增加“与 `doc/offical_resource` 金标准模板逐行对比（标题/表头/合并单元格）”校验

## ERP订单表结构扩展
- 主业务表（轻量）：
  - `GET /v1/erp/sales-order-lines`
  - `GET /v1/erp/production-orders`
- 原始明细表（拆分）：
  - 头表：`GET /v1/erp/sales-order-headers-raw`、`GET /v1/erp/production-order-headers-raw`
  - 行表：`GET /v1/erp/sales-order-lines-raw`、`GET /v1/erp/production-order-lines-raw`
- 主业务表仅保留稳定业务字段 + `erp_*` 引用键（`erp_record_id`、`erp_line_id`），不再展开动态海量列。
- 默认从 `mvp.erp.sqlite-path` 指向的 SQLite 文件读取真实 ERP 数据；读取失败时回退到 MVP 内置模拟数据。

## 约束
- 涉及周计划/月计划字段的改动必须先对齐 `02_金标准报表数据层对齐_周计划与月计划.md`。
- 不允许前端映射兜底补齐金标准字段，缺失必须在后端数据层修复。

## 运行口径
- 后端默认 profile 为 `local`（H2 内存库），本地直接运行无需先启动 PostgreSQL。
- 一键启动命令：仓库根目录执行 `.\run_stack.ps1`（或 `.\run_stack.bat`）。
- 默认地址：前端 `http://localhost:5932`，后端 `http://localhost:5931`。
- 前端默认 API 基址：`http://localhost:5931`；后端 CORS 默认放行：`http://localhost:5932`。
