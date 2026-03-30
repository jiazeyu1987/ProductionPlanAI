你是子团队 C（前端 LiteSchedulerPage 拆分负责人）。

任务目标：将 `code/frontend/src/pages/LiteSchedulerPage.jsx`（约2631行）做第一轮组件化与hooks化拆分，保持 S0（页面行为、接口调用、路由不变）。

你的唯一写入归属：
1. `code/frontend/src/pages/LiteSchedulerPage.jsx`
2. 新增文件仅允许在：`code/frontend/src/features/lite-scheduler/**`
3. 测试文件仅允许在：`code/frontend/src/features/lite-scheduler/**/*.test.js` 或 `code/frontend/src/pages/LiteSchedulerPage.test.jsx`

硬约束：
1. 不回滚他人改动，不跨界修改其他页面。
2. 不修改接口契约，不改URL参数语义。
3. 优先提取：纯工具函数、复杂派生状态、命令处理逻辑、可复用UI片段。
4. 每完成一小步跑验证，至少一次完整验证：
   - `cd code/frontend && npm test -- --run`
   - `cd code/frontend && npm run build`

建议拆分方向：
1. `useLiteSchedulerData`（拉取+归一化）
2. `useLiteSchedulerActions`（命令提交/保存/回滚）
3. `liteSchedulerViewModel`（大段useMemo派生）
4. 页面分块组件（过滤区、主表、详情区）

交付要求：
1. 直接改代码。
2. 最终回复：变更文件清单、关键设计说明、测试命令与结果、遗留风险、下一步建议。
3. 标注主文件行数变化（before/after）。
