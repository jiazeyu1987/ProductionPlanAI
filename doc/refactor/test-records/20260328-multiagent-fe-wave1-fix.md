# 重构测试记录

1. 重构步编号：`FE-Wave1-MultiAgent-Fix-A-C`
2. 波次（Wave）：`Wave-1`
3. 变更模块：
   1. Team-A `schedule`
   2. Team-C `masterdata`
4. 变更文件：
   1. `code/frontend/src/pages/ScheduleBoardPage.jsx`
   2. `code/frontend/src/pages/ScheduleVersionsPage.jsx`
   3. `code/frontend/src/pages/ScheduleCalendarPage.jsx`
   4. `code/frontend/src/features/schedule/apiClient.js`
   5. `code/frontend/src/pages/MasterdataPage.jsx`
   6. `code/frontend/src/features/masterdata/apiClient.js`
5. 变更说明：
   1. Team-A：调度相关页面改为调用 `features/schedule` client。
   2. Team-C：主数据页面改为调用 `features/masterdata` client。
   3. 接口路径、参数、请求行为保持不变。
6. 契约影响：`S0`（无接口契约变化）
7. 执行测试命令：
   1. `cd code/frontend && npm test -- MasterdataPage.test.jsx`
   2. `cd code/frontend && npm test -- ScheduleCalendarPage.test.jsx`
   3. `cd code/frontend && npm test`
   4. `cd code/frontend && npm run build`
8. 测试结果：
   1. 单页测试通过。
   2. 全量前端测试通过（`6 files / 49 tests passed`）。
   3. 前端构建通过（Vite build success）。
9. 风险与回滚：
   1. 风险：页面调用入口由通用 API 改为域 client，后续若函数签名变更需同步页面。
   2. 回滚：按模块回滚上述文件到改造前版本即可，不涉及数据迁移。
10. 是否允许进入下一步：`YES`
