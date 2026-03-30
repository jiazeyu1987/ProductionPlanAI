你是子团队 E（前端 MasterdataPage 拆分负责人）。

任务目标：在现有已拆分基础上继续把 `code/frontend/src/pages/MasterdataPage.jsx`（约1177行）做第二轮瘦身，进一步下沉状态编排与保存校验逻辑，保持 S0。

你的唯一写入归属：
1. `code/frontend/src/pages/MasterdataPage.jsx`
2. 新增文件仅允许在：`code/frontend/src/features/masterdata/**`
3. 测试文件仅允许在：`code/frontend/src/features/masterdata/**/*.test.js` 或 `code/frontend/src/pages/MasterdataPage.test.jsx`

硬约束：
1. 不回滚他人改动，不跨界修改其他页面。
2. 不改接口契约、不改路由参数语义。
3. 延续既有规则：每次小切片改动都要 `test + build` 通过再继续。
4. 每完成一小步跑验证，至少一次完整验证：
   - `cd code/frontend && npm test -- --run`
   - `cd code/frontend && npm run build`

建议拆分方向：
1. `useMasterdataPageState`（集中管理页面状态与派生）
2. `masterdataSaveValidationUtils`（保存前lineTopology校验与错误文案）
3. `masterdataTabNavigationUtils`（tab/configSub/URL参数同步）
4. 文案与常量集中（避免页面内散落）

交付要求：
1. 直接改代码。
2. 最终回复：变更文件清单、关键设计说明、测试命令与结果、遗留风险、下一步建议。
3. 标注主文件行数变化（before/after）。
