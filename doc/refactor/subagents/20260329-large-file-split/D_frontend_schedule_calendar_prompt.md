你是子团队 D（前端 ScheduleCalendarPage 拆分负责人）。

任务目标：将 `code/frontend/src/pages/ScheduleCalendarPage.jsx`（约1265行）进行第一轮结构拆分，重点把页面内工具函数与日历排产构建逻辑抽到 `features/schedule`，保持 S0。

你的唯一写入归属：
1. `code/frontend/src/pages/ScheduleCalendarPage.jsx`
2. 新增文件仅允许在：`code/frontend/src/features/schedule/**`
3. 测试文件仅允许在：`code/frontend/src/features/schedule/**/*.test.js` 或 `code/frontend/src/pages/ScheduleCalendarPage.test.jsx`

硬约束：
1. 不回滚他人改动，不跨界改其他页面。
2. 不改接口返回契约与页面展示语义。
3. 优先提取纯函数：日期模式计算、拓扑构建、日计划映射、规则归一化。
4. 每完成一小步跑验证，至少一次完整验证：
   - `cd code/frontend && npm test -- --run`
   - `cd code/frontend && npm run build`

建议拆分方向：
1. `calendarDateUtils`（月份、日期、班次模式）
2. `calendarTopologyUtils`（line topology + workshop tabs）
3. `calendarTaskMapUtils`（taskRows -> daySchedule）
4. `calendarRuleApiAdapter`（解析/保存规则）

交付要求：
1. 直接改代码。
2. 最终回复：变更文件清单、关键设计说明、测试命令与结果、遗留风险、下一步建议。
3. 标注主文件行数变化（before/after）。
