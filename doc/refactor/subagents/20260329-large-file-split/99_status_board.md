# 6-Agent 并行状态板（2026-03-29）

## 目标文件
1. backend: `ErpSqliteOrderLoader.java`
2. backend: `ErpDataManager.java`
3. frontend: `LiteSchedulerPage.jsx`
4. frontend: `ScheduleCalendarPage.jsx`
5. frontend: `MasterdataPage.jsx`
6. frontend: `OrdersPoolPage.jsx`

## 启动与状态
1. Agent A (`019d37ea-4e2d-76c0-9566-61d67556a45f`): 已返回，但首轮只产出执行/提示词文档；已下发纠偏指令要求执行真实拆分。
2. Agent B (`019d37ea-632a-7270-8a70-c64ea98b28d6`): 运行中（等待回传）。
3. Agent C (`019d37ea-780b-7fd3-9bce-7c3736686dd9`): 502 失败，已关闭。
4. Agent D (`019d37ea-8ce9-7520-b1a6-1da4f4cfb187`): 运行中（等待回传）。
5. Agent E (`019d37ea-a1ce-70e3-b45b-20d08d1ed1fe`): 502 失败，已关闭。
6. Agent F (`019d37ea-b6e5-7871-af77-3a62c6ad3923`): 502 失败，已关闭。

## 替补
1. Agent C2 (`019d382a-57fd-76c0-9b40-f704fb9d02ad`): 已拉起，负责 LiteSchedulerPage。
2. Agent E2 (`019d382a-6d26-79d1-8c09-2c93c84005b5`): 已拉起，负责 MasterdataPage。
3. Agent F2: 替补启动中（若通道异常持续，将由主负责人手动接管该模块首轮拆分）。

## 统一门禁（必须）
- Frontend: `cd code/frontend && npm test -- --run` + `npm run build`
- Backend: `cd code/backend && mvn -q -DskipTests compile` + `mvn -q -Dtest=MvpApiTest test`

## 负责人承诺
- 每个模块只允许其归属路径写入，避免并行冲突。
- 每个模块只做第一轮可合并拆分，不扩需求。
- 每个模块门禁通过后再进入下一轮。
