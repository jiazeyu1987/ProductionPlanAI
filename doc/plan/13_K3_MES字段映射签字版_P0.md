# K3/MES 字段映射签字版（P0）
> 文档属性：现行执行规范（跨系统字段映射签字件）。  
> 生效日期：2026-03-22。  
> 目标：让业务部门、信息化、实施方对“源字段 -> 目标字段 -> 转换规则 -> 责任人”形成一份可追责签字稿。

## 1. 交付要求（业务部门）
1. 每个数据集必须提交字段映射明细，不允许“口头约定字段”。
2. 映射必须明确：字段类型、可空规则、取值范围、枚举映射、时区规则。
3. 每个数据集必须有三方签字：业务负责人、信息化负责人、实施负责人。
4. 上线前签字版本冻结，后续变更必须走变更审批单。

## 2. 必交文件
1. 主映射表：`doc/plan/templates/K3_MES字段映射签字表_P0.csv`
2. 枚举映射表：`doc/plan/templates/K3_MES枚举映射签字表_P0.csv`
3. 样例数据包：按 `doc/plan/12_业务部门外部数据提供规范_P0.md` 提交。
4. 签署页扫描件：`doc/plan/templates/K3_MES字段映射签署页_P0.md`（电子签可替代）。

## 3. 主映射表字段定义
1. `source_system`：来源系统（`K3`/`MES`/`HR`）。
2. `dataset_name`：数据集名（必须与交付规范一致）。
3. `source_table`：来源表/来源视图名。
4. `source_field`：来源字段名。
5. `source_type`：来源类型（`string/int/decimal/datetime/enum`）。
6. `target_table`：目标表名。
7. `target_field`：目标字段名。
8. `target_type`：目标类型。
9. `transform_rule`：转换规则（格式、裁剪、默认值、单位换算）。
10. `enum_mapping_rule`：枚举映射引用（如 `order_type_map_v1`）。
11. `timezone_rule`：时间处理规则（统一 `Asia/Shanghai`）。
12. `nullable`：是否可空（`Y/N`）。
13. `owner_dept`：责任部门。
14. `owner_person`：责任人。
15. `reviewer`：审核人。
16. `sign_status`：`DRAFT/SIGNED`。
17. `sign_date`：签字日期。
18. `remarks`：备注。

## 4. 枚举映射签字表字段定义
1. `mapping_name`：映射名称（例如 `order_type_map_v1`）。
2. `source_system`：来源系统。
3. `dataset_name`：数据集名。
4. `source_field`：来源枚举字段。
5. `source_value`：来源值。
6. `target_value`：目标值。
7. `target_meaning`：业务含义。
8. `owner_person`：责任人。
9. `reviewer`：审核人。
10. `sign_status`：`DRAFT/SIGNED`。
11. `sign_date`：签字日期。

## 5. 签字判定标准
1. 所有必填数据集均已覆盖字段映射。
2. 所有时间字段均写明时区处理规则。
3. 所有枚举字段均写明源值到目标值映射。
4. 所有可空字段均有业务解释。
5. 责任人与审核人不能为同一人。
6. `sign_status` 必须全部为 `SIGNED` 才可进入上线检查清单。

## 6. 变更规则
1. 已签字映射若发生字段新增/改名/语义变更，必须发起 `变更审批单_P0`。
2. 变更通过后更新映射版本号（例：`v1 -> v1.1`）。
3. 变更后需补充样例数据并重新跑校验脚本。

## 7. 本次签字结论页
| 数据集 | 业务负责人 | 信息化负责人 | 实施负责人 | 结论 | 日期 |
|---|---|---|---|---|---|
| sales_order_line_fact |  |  |  | 通过/不通过 |  |
| production_order_fact |  |  |  | 通过/不通过 |  |
| schedule_control_fact |  |  |  | 通过/不通过 |  |
| process_route |  |  |  | 通过/不通过 |  |
| employee_skill |  |  |  | 通过/不通过 |  |
| shift_calendar |  |  |  | 通过/不通过 |  |
| equipment_process_capability |  |  |  | 通过/不通过 |  |
| reporting_fact |  |  |  | 通过/不通过 |  |
| material_availability |  |  |  | 通过/不通过 |  |

结论说明：
1. 若任一核心数据集“不通过”，则本批次不能进入上线发布。
2. 通过后，映射签字版作为发布包必备附件归档。
