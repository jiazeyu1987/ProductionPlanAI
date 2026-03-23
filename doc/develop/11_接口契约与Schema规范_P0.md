# 接口契约与Schema规范（P0，现行）
> 文档属性：现行开发规范。  
> 生效日期：2026-03-22。  
> 冲突处理：与其他文档冲突时，以 `doc/00_现行口径基线_2026-03-21.md` 为准。

## 1. 目的与适用范围
1. 本文将接口从“字段摘要”提升为“可执行契约”，用于联调、开发、测试、验收一致落地。  
2. 适用接口：ERP入站、报表输出接口、MES入站、ERP回写、系统内事件。  
3. 本文是 OpenAPI 文档与 Mock 服务的口径来源。

## 2. 通用契约（必须执行）
1. 协议：`HTTPS + JSON`。
2. 鉴权：`Authorization: Bearer <token>`。
3. 时区：`Asia/Shanghai`。
4. 时间：ISO8601（含时区），示例：`2026-03-22T08:30:00+08:00`。
5. 分页：`page`（从1开始）+ `page_size`（默认200，最大1000）。
6. 增量：`updated_after`（闭区间之后，按 `last_update_time asc` 返回）。
7. 幂等：写接口必须传 `request_id`；幂等键为 `request_id + 业务主键`。
8. 删除语义：禁止物理删除作为常规同步；关闭/冻结走状态字段。
9. 返回头：所有响应必须返回 `x-request-id`。
10. 路径口径：第 5~8 章中的路径均为 OpenAPI 相对路径；实际联调调用必须补全契约前缀（外部接口为 `/v1` + 相对路径，例如 `GET /v1/erp/sales-order-lines`、`POST /v1/internal/replan-jobs`）。

## 3. 统一错误响应体
```json
{
  "request_id": "req-20260322-0001",
  "code": "VALIDATION_ERROR",
  "message": "required field missing: production_order_no",
  "details": [
    {
      "field": "production_order_no",
      "reason": "required"
    }
  ],
  "retryable": false,
  "timestamp": "2026-03-22T08:30:12+08:00"
}
```

错误码约束：
1. `VALIDATION_ERROR` 对应 HTTP `400`。
2. `AUTH_ERROR` 对应 HTTP `401/403`。
3. `NOT_FOUND` 对应 HTTP `404`。
4. `CONFLICT` 对应 HTTP `409`。
5. `RATE_LIMITED` 对应 HTTP `429`（`retryable=true`）。
6. `INTERNAL_ERROR` 对应 HTTP `5xx`（`retryable=true`）。

## 4. 枚举字典（接口级）
1. `order_type`：`sales` / `production`。
2. `shift_code`：`DAY` / `NIGHT`。
3. `release_type`：`PURCHASE` / `OUTSOURCE` / `PRODUCTION`。
4. `skill_level`：`INDEPENDENT` / `ASSIST` / `NONE`。
5. `dependency_type`：`FS` / `SS`。
6. `command_type`：`INSERT` / `LOCK` / `UNLOCK` / `PRIORITY` / `FREEZE` / `UNFREEZE`。

## 5. ERP入站接口契约

### 5.1 GET /erp/sales-order-lines
请求参数：`updated_after,page,page_size`。

响应 `items[]` Schema：
1. `sales_order_no` string required
2. `line_no` string required
3. `product_code` string required
4. `order_qty` number required (`>=0`)
5. `order_date` datetime required
6. `expected_due_date` datetime required
7. `requested_ship_date` datetime required
8. `urgent_flag` integer required (`0/1`)
9. `order_status` string required
10. `last_update_time` datetime required
11. `erp_source_table` string required
12. `erp_record_id` string required
13. `erp_line_no` string required
14. `erp_line_id` string required
15. `erp_run_id` integer required
16. `erp_fid` integer required
17. `erp_form_id` string optional
18. `erp_document_status` string optional
19. `erp_fetched_at` datetime optional

说明：该接口为销售订单“主业务事实表（轻量）”，不展开 ERP 动态海量列。

### 5.2 GET /erp/sales-order-headers-raw
响应 `items[]` Schema：
1. `erp_source_table` string required
2. `erp_record_id` string required
3. `erp_run_id` integer optional
4. `erp_fid` integer required
5. `erp_bill_no` string required
6. `erp_bill_date` datetime optional
7. `erp_modify_date` datetime optional
8. `erp_document_status` string optional
9. `erp_org_no` string optional
10. `erp_fetched_at` datetime optional
11. `erp_form_id` string optional
12. `erp_header_json` string required

### 5.3 GET /erp/sales-order-lines-raw
响应 `items[]` Schema：
1. `erp_source_table` string required
2. `erp_record_id` string required
3. `erp_line_no` string required
4. `erp_line_id` string required
5. `erp_bill_no` string optional
6. `erp_fid` integer optional
7. `erp_form_id` string optional
8. `erp_line_json` string required

### 5.4 GET /erp/plan-orders
响应 `items[]` Schema：
1. `plan_order_no` string required
2. `source_sales_order_no` string required
3. `source_line_no` string required
4. `release_type` enum required
5. `release_status` string required
6. `release_time` datetime required
7. `last_update_time` datetime required

### 5.5 GET /erp/production-orders
响应 `items[]` Schema：
1. `production_order_no` string required
2. `source_sales_order_no` string required
3. `source_line_no` string required
4. `source_plan_order_no` string required
5. `material_list_no` string optional
6. `product_code` string required
7. `product_name_cn` string optional
8. `plan_qty` number required (`>=0`)
9. `production_status` string required
10. `last_update_time` datetime required
11. `erp_source_table` string required
12. `erp_record_id` string required
13. `erp_line_no` string required
14. `erp_line_id` string required
15. `erp_run_id` integer required
16. `erp_fid` integer required
17. `erp_form_id` string optional
18. `erp_document_status` string optional
19. `erp_fetched_at` datetime optional

月计划字段（可选，按金标准报表结构保留）：
1. `order_date`
2. `customer_remark`
3. `spec_model`
4. `production_batch_no`
5. `planned_finish_date_1`
6. `planned_finish_date_2`
7. `production_date_foreign_trade`
8. `packaging_form`
9. `sales_order_no`
10. `purchase_due_date`
11. `injection_due_date`
12. `market_remark_info`
13. `market_demand`
14. `semi_finished_code`
15. `semi_finished_inventory`
16. `semi_finished_demand`
17. `semi_finished_wip`
18. `need_order_qty`
19. `pending_inbound_qty`
20. `weekly_monthly_process_plan`
21. `workshop_outer_packaging_date`
22. `note`
23. `workshop_completed_qty`
24. `workshop_completed_time`
25. `outer_completed_qty`
26. `outer_completed_time`
27. `match_status`

说明：该接口为生产订单“主业务事实表（轻量）”，通过 `erp_record_id/erp_line_id` 关联原始表。

### 5.6 GET /erp/production-order-headers-raw
响应 `items[]` Schema：
1. `erp_source_table` string required
2. `erp_record_id` string required
3. `erp_run_id` integer optional
4. `erp_fid` integer required
5. `erp_bill_no` string required
6. `erp_bill_date` datetime optional
7. `erp_modify_date` datetime optional
8. `erp_document_status` string optional
9. `erp_org_no` string optional
10. `erp_fetched_at` datetime optional
11. `erp_form_id` string optional
12. `erp_header_json` string required

### 5.7 GET /erp/production-order-lines-raw
响应 `items[]` Schema：
1. `erp_source_table` string required
2. `erp_record_id` string required
3. `erp_line_no` string required
4. `erp_line_id` string required
5. `erp_bill_no` string optional
6. `erp_fid` integer optional
7. `erp_form_id` string optional
8. `erp_line_json` string required

### 5.8 GET /erp/schedule-controls
响应 `items[]` Schema：
1. `order_no` string required
2. `order_type` enum required
3. `review_passed_flag` integer required (`0/1`)
4. `frozen_flag` integer required (`0/1`)
5. `schedulable_flag` integer required (`0/1`)
6. `close_flag` integer required (`0/1`)
7. `promised_due_date` datetime required
8. `last_update_time` datetime required

### 5.9 GET /erp/mrp-links
响应 `items[]` Schema：
1. `order_no` string required
2. `order_type` enum required
3. `mrp_run_id` string required
4. `run_time` datetime required
5. `last_update_time` datetime required

### 5.10 GET /erp/delivery-progress
响应 `items[]` Schema：
1. `order_no` string required
2. `order_type` enum required
3. `warehoused_qty` number required (`>=0`)
4. `shipped_qty` number required (`>=0`)
5. `delivery_status` string required
6. `last_update_time` datetime required

### 5.11 GET /erp/material-availability（P1增强）
响应 `items[]` Schema：
1. `material_code` string required
2. `order_no` string required
3. `process_code` string required
4. `available_qty` number required (`>=0`)
5. `available_time` datetime required
6. `ready_flag` integer required (`0/1`)
7. `last_update_time` datetime required

### 5.12 GET /reports/workshop-weekly-plan
响应 `items[]` Schema：
1. `production_order_no` string required
2. `customer_remark` string required
3. `product_name` string required
4. `spec_model` string required
5. `production_batch_no` string required
6. `order_qty` number required
7. `packaging_form` string required
8. `sales_order_no` string required
9. `workshop_outer_packaging_date` string required
10. `process_schedule_remark` string required

### 5.13 GET /reports/workshop-monthly-plan
响应 `items[]` Schema：
1. `order_date` string required
2. `production_order_no` string required
3. `customer_remark` string required
4. `product_name` string required
5. `spec_model` string required
6. `production_batch_no` string required
7. `planned_finish_date_2` string required
8. `production_date_foreign_trade` string required
9. `order_qty` number required
10. `packaging_form` string required
11. `sales_order_no` string required
12. `purchase_due_date` string required
13. `injection_due_date` string required
14. `market_remark_info` string required
15. `market_demand` number required
16. `planned_finish_date_1` string required
17. `semi_finished_code` string required
18. `semi_finished_inventory` number required
19. `semi_finished_demand` number required
20. `semi_finished_wip` number required
21. `need_order_qty` number required
22. `pending_inbound_qty` number required
23. `weekly_monthly_process_plan` string required
24. `workshop_outer_packaging_date` string required
25. `note` string required
26. `workshop_completed_qty` number required
27. `workshop_completed_time` string required
28. `outer_completed_qty` number required
29. `outer_completed_time` string required
30. `match_status` string required

### 5.14 GET /reports/workshop-weekly-plan/export
响应体：`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，文件名 `workshop-weekly-plan.xlsx`。

### 5.15 GET /reports/workshop-monthly-plan/export
响应体：`application/vnd.ms-excel`，文件名 `workshop-monthly-plan.xls`。

## 6. MES入站接口契约

### 6.1 GET /mes/equipments
响应 `items[]` Schema：
1. `equipment_code` string required
2. `workshop_code` string required
3. `line_code` string required
4. `status` string required
5. `capacity_per_shift` number required (`>=0`)
6. `last_update_time` datetime required

### 6.2 GET /mes/process-routes
响应 `items[]` Schema：
1. `route_no` string required
2. `product_code` string required
3. `process_code` string required
4. `sequence_no` integer required
5. `dependency_type` enum required
6. `capacity_per_shift` number required
7. `required_manpower_per_group` integer required
8. `required_equipment_count` integer required
9. `last_update_time` datetime required

### 6.3 GET /mes/reportings
响应 `items[]` Schema：
1. `report_id` string required
2. `order_no` string required
3. `order_type` enum required
4. `process_code` string required
5. `report_qty` number required (`>=0`)
6. `report_time` datetime required
7. `shift_code` enum required
8. `team_code` string optional
9. `created_at` datetime required

### 6.4 GET /mes/equipment-process-capabilities
响应 `items[]` Schema：
1. `equipment_code` string required
2. `process_code` string required
3. `enabled_flag` integer required (`0/1`)
4. `capacity_factor` number required (`>0`)
5. `last_update_time` datetime required

### 6.5 GET /mes/employee-skills
响应 `items[]` Schema：
1. `employee_id` string required
2. `process_code` string required
3. `skill_level` enum required
4. `efficiency_factor` number required (`>0`)
5. `active_flag` integer required (`0/1`)
6. `last_update_time` datetime required

### 6.6 GET /mes/shift-calendar
响应 `items[]` Schema：
1. `calendar_date` date required
2. `shift_code` enum required
3. `shift_start_time` datetime required
4. `shift_end_time` datetime required
5. `open_flag` integer required (`0/1`)
6. `workshop_code` string required
7. `last_update_time` datetime required

## 7. ERP回写接口契约

### 7.1 POST /erp/schedule-results
请求体：
1. `request_id` string required
2. `schedule_version` string required
3. `items[]` required
4. `items[].order_no` string required
5. `items[].order_type` enum required
6. `items[].plan_start_time` datetime required
7. `items[].plan_finish_time` datetime required
8. `items[].priority` integer required
9. `items[].lock_flag` integer required (`0/1`)

### 7.2 POST /erp/schedule-status
请求体：
1. `request_id` string required
2. `schedule_version` string required
3. `items[]` required
4. `items[].order_no` string required
5. `items[].status` string required

## 8. 内部事件契约

### 8.1 POST /internal/wip-lots
1. `request_id` string required
2. `wip_lot_id` string required
3. `order_no` string required
4. `process_code` string required
5. `qty` number required (`>=0`)
6. `event_time` datetime required

### 8.2 POST /internal/replan-jobs
1. `request_id` string required
2. `trigger_type` enum required（`DELAY/PROGRESS_GAP/EQUIPMENT_DOWN/INSERT_ORDER`）
3. `scope_type` enum required（`LOCAL/GLOBAL`）
4. `base_version_no` string required
5. `reason` string required

## 9. 兼容与版本策略
1. URL主版本：`/v1`。
2. 破坏性变更：新增 `/v2`，禁止覆盖 `/v1`。
3. 非破坏性变更：仅允许新增 optional 字段，不允许修改字段语义。
4. 兼容窗口：旧版本至少保留 90 天。

## 10. 验收与交付件
1. 必须产出 OpenAPI 文件：`openapi/scheduling-v1.yaml`。
2. 必须产出字段级 Mock：`mock/scheduling-v1/*.json`。
3. 接口联调结论必须包含：通过率、失败样本、重试情况、幂等验证证据。

当前仓库已落地产物：
1. `openapi/scheduling-v1.yaml`
2. `mock/scheduling-v1/README.md` 及对应接口样例 JSON
