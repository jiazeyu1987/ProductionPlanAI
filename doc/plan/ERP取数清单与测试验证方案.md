# ERP取数清单与测试验证方案（排产系统）

## 1. 目标

为排产系统准备 ERP（K3）侧最小可用数据，并通过测试程序验证数据是否满足接入标准。

当前约束：
- 排产 P0 先不做库存精算。
- 设备/工艺/报工来自 MES。
- ERP 负责订单、交期、准入状态、MRP关联、计划订单投放链路与交付状态。

## 2. ERP需要提供的数据（P0最小集）

### 2.1 数据集：`sales_order_line`（销售订单行）

必需字段：
- `sales_order_no`：销售订单号
- `line_no`：订单行号
- `product_code`：产品编码
- `order_qty`：订单数量
- `order_date`：下单日期
- `expected_due_date`：期望交期
- `requested_ship_date`：要求发货日期
- `urgent_flag`：加急标识（0/1）
- `order_status`：订单状态
- `last_update_time`：更新时间

扩展字段（P0非必需）：
- `customer_code`、`customer_name`、`product_name`、`spec_model`、`uom`、`priority_code`

主键：
- `sales_order_no + line_no`

### 2.2 数据集：`plan_order`（计划订单）

必需字段：
- `plan_order_no`
- `source_sales_order_no`
- `source_line_no`
- `release_type`（`PURCHASE` / `OUTSOURCE` / `PRODUCTION`）
- `release_status`
- `release_time`
- `last_update_time`

扩展字段（P0非必需）：
- `mrp_run_id`、`planner_code`

主键：
- `plan_order_no`

### 2.3 数据集：`production_order`（生产订单）

必需字段：
- `production_order_no`
- `source_sales_order_no`
- `source_line_no`
- `product_code`
- `plan_qty`
- `plan_start_date`
- `plan_finish_date`
- `production_status`
- `last_update_time`

扩展字段（P0非必需）：
- `workshop_code`、`line_code`、`source_plan_order_no`、`material_list_no`

主键：
- `production_order_no`

### 2.4 数据集：`schedule_control`（排产准入控制）

必需字段：
- `order_no`
- `order_type`（枚举固定：`sales` / `production`）
- `review_passed_flag`（0/1）
- `promised_due_date`
- `frozen_flag`（0/1）
- `schedulable_flag`（0/1）
- `last_update_time`

扩展字段（P0非必需）：
- `review_status`、`scheduler_note`

主键：
- `order_no + order_type`

### 2.5 数据集：`mrp_result_link`（MRP结果关联）

必需字段：
- `order_no`
- `order_type`
- `mrp_run_id`
- `run_time`

扩展字段（P0非必需）：
- `purchase_req_no`、`outsource_req_no`、`make_order_no`

主键：
- `order_no + order_type + mrp_run_id`

### 2.6 数据集：`delivery_progress`（交付进度）

必需字段：
- `order_no`
- `order_type`
- `warehoused_qty`
- `shipped_qty`
- `delivery_status`
- `last_update_time`

扩展字段（P0非必需）：
- `inspection_status`、`warehousing_status`

主键：
- `order_no + order_type`

## 3. ERP导出格式要求

支持两种方式：

1. 单个 Excel 文件（推荐）
- 文件名：任意
- Sheet 名必须为：
  - `sales_order_line`
  - `plan_order`
  - `production_order`
  - `schedule_control`
  - `mrp_result_link`
  - `delivery_progress`

2. 文件夹形式
- 每个数据集一个文件，文件名必须为：
  - `sales_order_line.csv` 或 `.xlsx`
  - `plan_order.csv` 或 `.xlsx`
  - `production_order.csv` 或 `.xlsx`
  - `schedule_control.csv` 或 `.xlsx`
  - `mrp_result_link.csv` 或 `.xlsx`
  - `delivery_progress.csv` 或 `.xlsx`

## 4. 测试程序校验内容

测试脚本：`scripts/validate_erp_extract.py`

自动校验项：
1. 是否缺少数据集（6个是否齐全）。
2. 字段是否缺失（必需字段/扩展字段）。
3. 必需字段是否为空。
4. 主键是否重复。
5. 日期字段是否可解析。
6. 数值字段是否可解析。
7. 0/1字段是否合法（加急、冻结、可排产等）。
8. 跨表关联是否成立：
   - 计划订单来源销售订单是否存在。
   - 生产订单来源销售订单是否存在。
   - 生产订单来源计划订单是否存在（如提供 `source_plan_order_no`）。
   - 控制表/MRP表/交付表中的订单是否可在销售或生产订单中找到。
9. 业务规则检查：
   - `review_passed_flag=1` 时，`promised_due_date` 不能为空。

输出：
- JSON 报告（默认）：`doc/plan/erp_extract_validation_report.json`

## 5. 使用方法

### 5.1 生成导出模板（先给ERP同事）

```powershell
python scripts/validate_erp_extract.py --generate-template doc/plan/erp_extract_template.xlsx
```

### 5.2 校验 ERP 导出文件（Excel）

```powershell
python scripts/validate_erp_extract.py --input D:\data\erp_export.xlsx
```

### 5.3 校验 ERP 导出目录（CSV/XLSX）

```powershell
python scripts/validate_erp_extract.py --input D:\data\erp_export_dir
```

### 5.4 指定报告输出路径

```powershell
python scripts/validate_erp_extract.py --input D:\data\erp_export.xlsx --report-json doc/plan/erp_validation_round1.json
```

## 6. 对接执行顺序

1. 先让 ERP 导出 6 个数据集的一次全量（用于建模和规则确认）。
2. 跑验证脚本，先把“字段缺失、主键重复、日期格式错误”清零。
3. 再切到增量同步（按 `last_update_time`）。
4. 最后做联调：ERP订单 -> 排产结果 -> ERP回写状态闭环。
