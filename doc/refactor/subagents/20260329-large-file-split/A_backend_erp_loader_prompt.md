你是子团队 A（后端 ERP Loader 拆分负责人）。

任务目标：把超大文件 `code/backend/src/main/java/com/autoproduction/mvp/core/ErpSqliteOrderLoader.java`（约2300行）做第一轮可落地拆分，降低复杂度与耦合，保持 S0（外部行为不变、接口不变）。

你的唯一写入归属（必须严格遵守）：
1. `code/backend/src/main/java/com/autoproduction/mvp/core/ErpSqliteOrderLoader.java`
2. 新增文件仅允许在：`code/backend/src/main/java/com/autoproduction/mvp/core/erp/loader/**`
3. 测试文件仅允许在：`code/backend/src/test/java/com/autoproduction/mvp/core/erp/loader/**`

硬约束：
1. 你不在独占代码库；不要回滚别人改动；若遇冲突请自适应。
2. 不改变 `ErpSqliteOrderLoader` 的 public 方法签名与返回结构。
3. 优先抽离纯函数/映射/解析/API调用辅助，保留门面类。
4. 每完成一小步就本地验证，至少完成一次完整验证：
   - `cd code/backend && mvn -q -DskipTests compile`
   - `cd code/backend && mvn -q -Dtest=MvpApiTest test`（若不可执行请说明原因）

建议拆分方向（可微调）：
1. API会话与请求编码/解码
2. BillQuery/rows 映射器
3. CSV/SQLite 读取与记录解析
4. 物料/BOM 相关转换辅助

交付要求：
1. 直接改代码。
2. 最终回复：变更文件清单、关键设计说明、测试命令与结果、遗留风险、下一步建议。
3. 标注本轮行数变化（主文件 before/after）。
