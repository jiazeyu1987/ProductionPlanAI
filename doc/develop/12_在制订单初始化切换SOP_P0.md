# 在制订单初始化切换SOP（P0，现行）
> 文档属性：现行运行规范。  
> 生效日期：2026-03-22。  
> 适用场景：系统首次上线时存在在制订单（例如 5 个销售订单、10 个生产订单已在执行）。

## 1. 目标与原则
1. 目标：将历史在制状态无损迁入系统，形成首个可追溯基线版本。
2. 原则：`先冻结切换窗口 -> 全量快照 -> 校验 -> 生成基线版本 -> 放开增量`。
3. 原则：任何口头状态不计入迁移，必须有系统数据记录。

## 2. 角色与职责
1. 业务负责人：确认切换窗口、确认在制真实状态。
2. 计划负责人：确认锁单/冻结范围、发布基线版本。
3. 信息化：执行全量导入、校验、回写验证。
4. 生产/MES：提供工序级报工快照与设备状态快照。
5. ERP：提供订单链与准入状态全量快照。

## 3. 切换前准备（T-3 ~ T-1）
1. 确定切换时点 `T0`（示例：`2026-03-23 08:00:00 +08:00`）。
2. 确认切换窗口（建议 `T0-30min` 到 `T0+30min`）禁止手工改单。
3. 完成主数据快照准备：
   - `product/process/product_route_header/process_route/route_step_dependency`
   - `equipment_capacity/equipment_process_capability`
   - `employee/employee_skill/employee_shift_availability`
   - `shift_calendar`
4. 完成订单事实快照准备：
   - `sales_order_line_fact/plan_order/production_order_fact`
   - `schedule_control_fact/mrp_result_link/delivery_progress`
   - `production_material_list_item/material_availability`
5. 完成执行快照准备：`reporting_fact`（工序级累计进度）。
6. 准备回滚预案：数据库快照、旧流程恢复责任人、回滚触发条件。

## 4. 初始化执行步骤（T0当日）

### 4.1 冻结窗口
1. 暂停新发布和自动重排。
2. 暂停 ERP/MES 增量消费（只保留缓存队列，不丢数据）。
3. 导出 `T0` 时刻全量快照包。

### 4.2 数据导入
1. 先导入主数据与资源数据。
2. 再导入订单事实数据与控制状态。
3. 最后导入 `reporting_fact`、`delivery_progress`、`material_availability`。
4. 导入顺序必须保证关联先后（避免孤儿数据）。

### 4.3 质量校验（必须全部通过）
1. 主键重复检查：0条。
2. 必填缺失检查：0条。
3. 枚举非法检查：0条。
4. 时间格式非法检查：0条。
5. 关联一致性：
   - `sales_order_line_fact -> plan_order -> production_order_fact` 全可追溯。
   - `production_order_fact -> schedule_control_fact` 全命中。
   - `reporting_fact.order_no` 必须可在订单池命中。
6. 进度合理性：`报工累计 <= plan_qty`。

### 4.4 基线版本生成与发布
1. 生成 `V1_BASELINE` 草稿版本。
2. 计划负责人核对：
   - 在制单已完成工序是否正确。
   - 未完成工序是否进入后续排程。
   - 锁单/冻结是否符合现场约束。
3. 发布 `V1_BASELINE`，并记录发布日志。

### 4.5 恢复增量
1. 开启 ERP/MES 增量消费。
2. 增量游标从 `T0` 开始，执行补偿同步一次。
3. 对账窗口：`T0` 后 2 小时内完成首轮对账。

## 5. 在制单特殊处理规则
1. 若订单已完工：`close_flag=1`，不进入可排清单。
2. 若订单部分完工：仅剩余工序参与排产。
3. 若订单已冻结/锁单：保留冻结/锁单，不参与自动重排。
4. 若报工已超计划：不回退报工，进入异常清单人工处理。
5. 若工序跳报：标记异常，禁止自动发布，需人工确认。

## 6. 5单10工单示例操作
1. 先准备 5 条 `sales_order_line_fact` 和关联 10 条 `production_order_fact`。
2. 将 10 条生产单全部写入 `schedule_control_fact`，明确可排/冻结/关闭状态。
3. 按工序写入 `reporting_fact` 累计记录，确保可推导“已完工工序”。
4. 物料与交付快照同步到 `material_availability`、`delivery_progress`。
5. 生成 `V1_BASELINE`，核对后发布。

## 7. 回滚触发与处置
触发任一条件即回滚：
1. 关键关联校验失败（>1%订单存在链路断裂）。
2. 在制进度误差超阈值（>3%订单累计报工不一致）。
3. 发布失败或回写连续失败 2 次。

回滚步骤：
1. 停止增量消费。
2. 回退到切换前数据库快照。
3. 恢复旧流程执行。
4. 形成故障复盘与二次切换计划。

## 8. 交付物清单
1. `init_full_snapshot_YYYYMMDDHHMM.zip`（全量快照包）。
2. `init_validation_report_YYYYMMDDHHMM.json`（校验报告）。
3. `baseline_publish_record_YYYYMMDDHHMM.md`（基线发布记录）。
4. `cutover_reconcile_report_YYYYMMDDHHMM.xlsx`（切换对账结果）。
