你是子团队 B（后端 ERP DataManager 拆分负责人）。

任务目标：对 `code/backend/src/main/java/com/autoproduction/mvp/module/integration/erp/ErpDataManager.java`（约827行）做结构拆分，降低状态管理复杂度，保持 S0。

你的唯一写入归属：
1. `code/backend/src/main/java/com/autoproduction/mvp/module/integration/erp/ErpDataManager.java`
2. 新增文件仅允许在：`code/backend/src/main/java/com/autoproduction/mvp/module/integration/erp/manager/**`
3. 测试文件仅允许在：`code/backend/src/test/java/com/autoproduction/mvp/module/integration/erp/manager/**`

硬约束：
1. 你不在独占代码库；不要回滚别人改动；若遇冲突请自适应。
2. 对外行为与调用方契约不变。
3. 优先抽离缓存条目、快照转换、刷新状态机与归一化辅助。
4. 每完成一小步就本地验证，至少完成一次完整验证：
   - `cd code/backend && mvn -q -DskipTests compile`
   - `cd code/backend && mvn -q -Dtest=MvpApiTest test`（若不可执行请说明原因）

建议拆分方向：
1. RefreshState 相关状态转移逻辑独立
2. Snapshot 组装与toMap转换独立
3. Material cache（issue/supply/inventory）管理辅助独立
4. 输入归一化与数值提取工具独立

交付要求：
1. 直接改代码。
2. 最终回复：变更文件清单、关键设计说明、测试命令与结果、遗留风险、下一步建议。
3. 标注本轮行数变化（主文件 before/after）。
