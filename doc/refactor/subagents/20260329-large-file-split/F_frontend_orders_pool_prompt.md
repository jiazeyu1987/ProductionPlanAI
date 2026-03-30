你是子团队 F（前端 OrdersPoolPage 拆分负责人）。

任务目标：拆分 `code/frontend/src/pages/OrdersPoolPage.jsx`（约945行），把订单筛选、物料树、状态计算、命令提交逻辑抽离，降低页面复杂度，保持 S0。

你的唯一写入归属：
1. `code/frontend/src/pages/OrdersPoolPage.jsx`
2. 新增文件仅允许在：`code/frontend/src/features/orders-pool/**`
3. 测试文件仅允许在：`code/frontend/src/features/orders-pool/**/*.test.js` 或 `code/frontend/src/pages/OrdersPoolPage.test.jsx`

硬约束：
1. 你不在独占代码库；不要回滚别人改动；若遇冲突请自适应。
2. API 调用与页面交互行为不变。
3. 优先抽离：material tree builder、status/percent formatter、query/filter selectors、command action helpers。
4. 每完成一小步跑验证，至少一次完整验证：
   - `cd code/frontend && npm test -- --run`
   - `cd code/frontend && npm run build`

建议拆分方向：
1. `ordersPoolFormatters`（状态、百分比、展示文案）
2. `ordersPoolMaterialTree`（树构建、展开收起、children加载辅助）
3. `ordersPoolSelectors`（筛选、统计、派生数据）
4. `ordersPoolActions`（命令提交与期望开工保存动作）

交付要求：
1. 直接改代码。
2. 最终回复：变更文件清单、关键设计说明、测试命令与结果、遗留风险、下一步建议。
3. 标注主文件行数变化（before/after）。
