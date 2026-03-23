# AutoProduction P0 MVP (code)

本目录已按 `doc/MVP` 与 `doc/develop` 现行规范切换为：
- 后端：`Java 21 + Spring Boot + PostgreSQL`（`code/backend`）
- 前端：`React`（`code/frontend`）
- 兼容参考：历史 Node 版本保留在 `code/legacy-node`（不作为主实现）

## 1. 目录
```text
code/
  backend/      # Spring Boot API + 排产引擎 + 契约路由
  frontend/     # React 页面（10个P0核心路由）
  legacy-node/  # 历史兼容代码
```

## 2. 后端能力（`code/backend`）
- 契约路由：
  - `GET/POST /v1/*`（外部契约）
  - `GET/POST /internal/v1/internal/*`（内部契约）
  - `GET/POST/PATCH /api/*`（兼容路由）
- 规则约束：
  - `1班次=12小时`
  - 人机料硬约束 + `FS/SS` 工序依赖
  - 锁单不参与自动重排
- 治理：
  - 写接口强制 `request_id`
  - `request_id + action` 幂等账本
  - 审计、预警、重排、发布/回滚
  - `job_task` 与 `integration inbox/outbox` 表结构

## 3. 前端能力（`code/frontend`）
- 已实现 P0 路由：
  - `/dashboard`
  - `/orders/pool`
  - `/schedule/board`
  - `/schedule/versions`
  - `/dispatch/commands`
  - `/execution/wip`
  - `/alerts`
  - `/audit/logs`
  - `/masterdata`
  - `/ops/integration`
  - `/simulation`
- 所有前端写操作自动携带 `request_id`。

## 4. 本地运行
### 4.1 Frontend
```bash
cd code/frontend
npm install
npm run dev
```
或直接执行：
```bash
cd code
powershell -ExecutionPolicy Bypass -File .\scripts\run_frontend.ps1
```

### 4.2 Backend
```bash
cd code/backend
mvn spring-boot:run
```

> 当前机器若缺少 `java/mvn`，请先安装 JDK 21 与 Maven。

### 4.3 Backend（无需 PostgreSQL 的本地模式）
```bash
cd code/backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```
或直接执行：
```bash
cd code
powershell -ExecutionPolicy Bypass -File .\scripts\run_backend_local.ps1
```

### 4.4 PostgreSQL 模式（推荐联调）
1. 启动 PostgreSQL（Docker）：
```bash
cd code
powershell -ExecutionPolicy Bypass -File .\scripts\run_postgres.ps1
```
2. 启动后端（连接 PostgreSQL）：
```bash
cd code
powershell -ExecutionPolicy Bypass -File .\scripts\run_backend_pg.ps1
```
3. 停止 PostgreSQL：
```bash
cd code
powershell -ExecutionPolicy Bypass -File .\scripts\stop_postgres.ps1
```

## 5. 测试与验证
### 5.1 后端测试
```bash
cd code/backend
mvn test
```

### 5.2 前端测试与构建
```bash
cd code/frontend
npm install
npm test
npm run build
```

### 5.3 一键验证（推荐）
```bash
cd code
powershell -ExecutionPolicy Bypass -File .\scripts\run_verify.ps1
```

### 5.4 PostgreSQL 栈验证（后端+数据库）
```bash
cd code
powershell -ExecutionPolicy Bypass -File .\scripts\verify_pg_stack.ps1
```

### 5.5 质量门禁与工件检查（MVP文档入口）
```bash
cd code
powershell -ExecutionPolicy Bypass -File .\scripts\run_quality_gate.ps1
python .\scripts\check_artifacts.py
```

## 6. 契约来源
- 外部接口：`openapi/scheduling-v1.yaml`
- 内部接口：`openapi/scheduling-internal-v1.yaml`
