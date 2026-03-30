# 6-Agent 超大文件拆分提示词（负责人版）

## Agent A - ErpSqliteOrderLoader（backend）
- Agent ID: `019d37ea-4e2d-76c0-9566-61d67556a45f`
- 归属文件：
  - `code/backend/src/main/java/com/autoproduction/mvp/core/ErpSqliteOrderLoader.java`
  - `code/backend/src/main/java/com/autoproduction/mvp/core/erp/loader/**`
  - `code/backend/src/test/java/com/autoproduction/mvp/core/erp/loader/**`
- 提示词：
```text
你是子团队 A（后端 ERP Loader 拆分负责人）。

任务目标：把超大文件 `code/backend/src/main/java/com/autoproduction/mvp/core/ErpSqliteOrderLoader.java`（约2300行）做第一轮可落地拆分，降低复杂度与耦合，保持 S0（外部行为不变、接口不变）。

你的唯一写入归属（必须严格遵守）：
1) `code/backend/src/main/java/com/autoproduction/mvp/core/ErpSqliteOrderLoader.java`
2) 新增文件仅允许在：`code/backend/src/main/java/com/autoproduction/mvp/core/erp/loader/**`
3) 测试文件仅允许在：`code/backend/src/test/java/com/autoproduction/mvp/core/erp/loader/**`

硬约束：
- 你不在独占代码库；不要回滚别人改动；若遇冲突请自适应。
- 不改变 `ErpSqliteOrderLoader` 的 public 方法签名与返回结构。
- 优先抽离纯函数/映射/解析/API调用辅助，保留门面类。
- 每完成一小步就本地验证，至少完成一次完整验证：
  - `cd code/backend && mvn -q -DskipTests compile`
  - `cd code/backend && mvn -q -Dtest=MvpApiTest test`（若不可执行请说明原因）

建议拆分方向（可微调）：
- API会话与请求编码/解码
- BillQuery/rows 映射器
- CSV/SQLite 读取与记录解析
- 物料/BOM 相关转换辅助

交付要求：
1) 直接改代码。
2) 最终回复给我：变更文件清单、关键设计说明、测试命令与结果、遗留风险、下一步建议。
3) 标注本轮行数变化（主文件前后行数）。
```

## Agent B - ErpDataManager（backend）
- Agent ID: `019d37ea-632a-7270-8a70-c64ea98b28d6`
- 归属文件：
  - `code/backend/src/main/java/com/autoproduction/mvp/module/integration/erp/ErpDataManager.java`
  - `code/backend/src/main/java/com/autoproduction/mvp/module/integration/erp/manager/**`
  - `code/backend/src/test/java/com/autoproduction/mvp/module/integration/erp/manager/**`
- 提示词：
```text
你是子团队 B（后端 ERP DataManager 拆分负责人）。

任务目标：对 `code/backend/src/main/java/com/autoproduction/mvp/module/integration/erp/ErpDataManager.java`（约827行）做结构拆分，降低状态管理复杂度，保持 S0。

你的唯一写入归属：
1) `code/backend/src/main/java/com/autoproduction/mvp/module/integration/erp/ErpDataManager.java`
2) 新增文件仅允许在：`code/backend/src/main/java/com/autoproduction/mvp/module/integration/erp/manager/**`
3) 测试文件仅允许在：`code/backend/src/test/java/com/autoproduction/mvp/module/integration/erp/manager/**`

硬约束：
- 你不在独占代码库；不要回滚别人改动；若遇冲突请自适应。
- 对外行为与调用方契约不变。
- 抽离缓存条目/快照转换/刷新状态转移逻辑（优先纯逻辑）。
- 每完成一小步做验证，至少一次完整验证：
  - `cd code/backend && mvn -q -DskipTests compile`
  - `cd code/backend && mvn -q -Dtest=MvpApiTest test`（若不可执行说明）

交付要求：
1) 直接改代码。
2) 汇报：变更文件清单、拆分说明、测试命令与结果、风险与后续。
3) 标注主文件前后行数。
```

## Agent C - LiteSchedulerPage（frontend）
- Agent ID: `019d37ea-780b-7fd3-9bce-7c3736686dd9`
- 归属文件：
  - `code/frontend/src/pages/LiteSchedulerPage.jsx`
  - `code/frontend/src/features/lite-scheduler/**`
- 提示词：
```text
你是子团队 C（前端 LiteSchedulerPage 拆分负责人）。

任务目标：拆分 `code/frontend/src/pages/LiteSchedulerPage.jsx`（约2631行），将页面中的纯逻辑、数据派生、动作处理拆到 `features/lite-scheduler`，保持 UI 与行为 S0。

你的唯一写入归属：
1) `code/frontend/src/pages/LiteSchedulerPage.jsx`
2) 新增/修改文件仅允许在：`code/frontend/src/features/lite-scheduler/**`
3) 新增测试仅允许在：`code/frontend/src/features/lite-scheduler/**` 或 `code/frontend/src/pages/LiteSchedulerPage.test.jsx`

硬约束：
- 你不在独占代码库；不要回滚别人改动；若遇冲突请自适应。
- 页面路由、接口调用契约、已有交互行为不变。
- 优先抽离：pure utils、selectors、action handlers/hooks（例如 `useLiteScheduler*`）。
- 每次小步改动后验证，至少完成一次完整门禁：
  - `cd code/frontend && npm test -- --run`
  - `cd code/frontend && npm run build`

交付要求：
1) 直接改代码。
2) 汇报：变更文件清单、拆分层次图（简述）、测试结果、风险。
3) 标注主文件前后行数。
```

## Agent D - ScheduleCalendarPage（frontend）
- Agent ID: `019d37ea-8ce9-7520-b1a6-1da4f4cfb187`
- 归属文件：
  - `code/frontend/src/pages/ScheduleCalendarPage.jsx`
  - `code/frontend/src/features/schedule/**`
- 提示词：
```text
你是子团队 D（前端 ScheduleCalendarPage 拆分负责人）。

任务目标：拆分 `code/frontend/src/pages/ScheduleCalendarPage.jsx`（约1265行），将日历算法/排程视图映射/规则保存逻辑抽离到 `features/schedule`，保持 S0。

你的唯一写入归属：
1) `code/frontend/src/pages/ScheduleCalendarPage.jsx`
2) 新增/修改文件仅允许在：`code/frontend/src/features/schedule/**`
3) 新增测试仅允许在：`code/frontend/src/features/schedule/**` 或 `code/frontend/src/pages/ScheduleCalendarPage.test.jsx`

硬约束：
- 你不在独占代码库；不要回滚别人改动；若遇冲突请自适应。
- API 参数、页面行为、日期模式语义不变。
- 优先抽离纯函数：日期/模式/拓扑构建/展示映射。
- 至少完成一次完整门禁：
  - `cd code/frontend && npm test -- --run`
  - `cd code/frontend && npm run build`

交付要求：
1) 直接改代码。
2) 汇报：变更文件清单、核心拆分说明、测试结果、风险。
3) 标注主文件前后行数。
```

## Agent E - MasterdataPage（frontend）
- Agent ID: `019d37ea-a1ce-70e3-b45b-20d08d1ed1fe`
- 归属文件：
  - `code/frontend/src/pages/MasterdataPage.jsx`
  - `code/frontend/src/features/masterdata/**`
- 提示词：
```text
你是子团队 E（前端 MasterdataPage 拆分负责人）。

任务目标：继续拆分 `code/frontend/src/pages/MasterdataPage.jsx`（当前约1177行），把页面 orchestration 中可抽离的逻辑继续下沉到 `features/masterdata`，保持 S0。

你的唯一写入归属：
1) `code/frontend/src/pages/MasterdataPage.jsx`
2) 新增/修改文件仅允许在：`code/frontend/src/features/masterdata/**`
3) 新增测试仅允许在：`code/frontend/src/features/masterdata/**` 或 `code/frontend/src/pages/MasterdataPage.test.jsx`

硬约束：
- 你不在独占代码库；不要回滚别人改动；若遇冲突请自适应。
- 必须维持现有页面行为和接口 payload 契约。
- 重点处理：保存前校验、行数据归一化、tab/日期切换衍生逻辑（先抽纯函数）。
- 注意文件内中文文案编码完整性，避免乱码回归。
- 至少完成一次完整门禁：
  - `cd code/frontend && npm test -- --run`
  - `cd code/frontend && npm run build`

交付要求：
1) 直接改代码。
2) 汇报：变更文件清单、拆分说明、测试结果、风险。
3) 标注主文件前后行数。
```

## Agent F - OrdersPoolPage（frontend）
- Agent ID: `019d37ea-b6e5-7871-af77-3a62c6ad3923`
- 归属文件：
  - `code/frontend/src/pages/OrdersPoolPage.jsx`
  - `code/frontend/src/features/orders-pool/**`
- 提示词：
```text
你是子团队 F（前端 OrdersPoolPage 拆分负责人）。

任务目标：拆分 `code/frontend/src/pages/OrdersPoolPage.jsx`（约945行），把订单筛选、物料树、状态计算、命令提交逻辑抽离，降低页面复杂度，保持 S0。

你的唯一写入归属：
1) `code/frontend/src/pages/OrdersPoolPage.jsx`
2) 新增/修改文件仅允许在：`code/frontend/src/features/orders-pool/**`
3) 新增测试仅允许在：`code/frontend/src/features/orders-pool/**` 或 `code/frontend/src/pages/OrdersPoolPage.test.jsx`

硬约束：
- 你不在独占代码库；不要回滚别人改动；若遇冲突请自适应。
- API 调用与页面交互行为不变。
- 优先抽离：material tree builder、status/percent formatter、query/filter selectors、command action helpers。
- 至少完成一次完整门禁：
  - `cd code/frontend && npm test -- --run`
  - `cd code/frontend && npm run build`

交付要求：
1) 直接改代码。
2) 汇报：变更文件清单、拆分说明、测试结果、风险。
3) 标注主文件前后行数。
```
