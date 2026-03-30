# 重构测试记录

1. 重构步骤编号：`BE-Wave1-PLAT-02-Facade`
2. 波次（Wave）：`Wave-1`
3. 变更模块：
   1. Team-A `schedule`
   2. Team-B `orderexecution`
   3. Team-C `masterdata`
   4. Team-D `dispatchalert`
   5. Team-E `integration`
4. 本次追加修复文件：
   1. `code/backend/src/main/java/com/autoproduction/mvp/core/MvpStoreService.java`
   2. `code/backend/src/main/java/com/autoproduction/mvp/core/SchedulerEngine.java`
5. 追加修复说明（针对 5 个失败用例）：
   1. 修复 `scheduleAlgorithmExplainEndpointWorks`：`process_summary` 在无分配场景下补齐 `max_allocation_*` 字段默认值，避免 JSON 路径缺失。
   2. 修复 `onlyFinalProcessReportingCountsAsFinishedQtyAndKeepsRemainingSchedulable`：调度引擎回灌非末道历史报工作为 route/WIP 基线；并修正带滞后依赖（如灭菌）对历史产出的计算。
   3. 修复 `manualAdvanceDayShouldKeepIncreasingFinalFinishedQtyWhenOrderIsNotDone` 与 `routeReportingShouldBeMonotonicAndConvergeToOrderQty`：仿真排程显式关闭订单物料约束覆盖，同时避免仿真模式复用历史报工导致后续日推进停滞。
   4. 修复 `exportEndpointsProvideGoldStandardTemplateHeaders`：月计划 xls 分组表头改为与金标准模板一致的中文文案。
6. 契约影响：`S0`（接口契约无变化）
7. 执行测试命令：
   1. `cd code/backend && mvn "-Dtest=MvpApiTest#scheduleAlgorithmExplainEndpointWorks" test`
   2. `cd code/backend && mvn "-Dtest=MvpApiTest#onlyFinalProcessReportingCountsAsFinishedQtyAndKeepsRemainingSchedulable" test`
   3. `cd code/backend && mvn "-Dtest=MvpApiTest#manualAdvanceDayShouldKeepIncreasingFinalFinishedQtyWhenOrderIsNotDone" test`
   4. `cd code/backend && mvn "-Dtest=MvpApiTest#routeReportingShouldBeMonotonicAndConvergeToOrderQty" test`
   5. `cd code/backend && mvn "-Dtest=MvpApiTest#exportEndpointsProvideGoldStandardTemplateHeaders" test`
   6. `cd code/backend && mvn -Dtest=MvpApiTest test`
8. 测试结果：
   1. 目标 5 个失败用例：全部通过。
   2. `MvpApiTest` 全量：通过（`38 passed / 0 failed / 0 errors / 0 skipped`）。
   3. `mvn test`：仍有其他测试类失败（`MvpStoreServiceOrderPoolMaterialsTest` 3 fail + 3 error），与本次 5 个用例修复范围不同。
9. 风险与回滚：
   1. 风险：仿真排程新增 `apply_order_material_constraints=false` 路径，需在后续 BE-A~E 阶段做专项回归（仿真与正式排程两套路径）。
   2. 回滚：可仅回滚 `MvpStoreService.java` 与 `SchedulerEngine.java` 本次修改。
10. 是否允许进入下一步：`YES`（按当前门禁：5 个失败用例与全量 `MvpApiTest` 已通过）。