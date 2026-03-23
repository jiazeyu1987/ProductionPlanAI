# RBAC权限矩阵与审批规范（P0，现行）
> 文档属性：现行开发规范。  
> 生效日期：2026-03-22。

## 1. 角色定义
1. `VIEWER`：查看者，只读。
2. `DISPATCHER`：调度员，可调序/插单/锁单/解锁。
3. `PUBLISHER`：发布员，可发布/回滚版本。
4. `ADMIN`：管理员，可配置规则、用户与权限。
5. `AUDITOR`：审计员，可查询和导出审计记录。

## 2. 权限矩阵（按钮/接口级）
1. 查看待排池/甘特图：`VIEWER/DISPATCHER/PUBLISHER/ADMIN`。
2. 创建人工指令（`dispatch_command`）：`DISPATCHER/PUBLISHER/ADMIN`。
3. 审批人工指令：`PUBLISHER/ADMIN`。
4. 锁单/解锁：`DISPATCHER/PUBLISHER/ADMIN`。
5. 冻结/解冻：`PUBLISHER/ADMIN`（解冻必须审批）。
6. 生成草稿版本：`DISPATCHER/PUBLISHER/ADMIN`。
7. 发布版本：`PUBLISHER/ADMIN`。
8. 回滚版本：`PUBLISHER/ADMIN`（二次确认）。
9. 修改规则参数：`ADMIN`。
10. 用户角色维护：`ADMIN`。
11. 审计日志导出：`AUDITOR/ADMIN`。

## 3. 高风险操作审批规则
以下操作必须审批：
1. 冻结窗内任务调整。
2. 批量锁单（>=20条）。
3. 解冻操作。
4. 回滚发布版本。
5. 手工改交期/改优先级权重。

审批要求：
1. 必填：申请人、原因、影响范围、生效时间。
2. 审批流：至少 1 级审批；重大操作可配置 2 级审批。
3. 未审批通过不得生效。

## 4. 权限校验技术要求
1. 前端按钮按权限隐藏/禁用。
2. 后端接口必须二次鉴权（禁止仅前端控制）。
3. 所有权限校验失败记录安全日志。
4. 权限变更生效需刷新 token（或下次登录生效，策略固定一种）。

## 5. 审计字段规范
所有关键操作必须记录：
1. `operator`
2. `operate_time`
3. `entity_type`
4. `entity_id`
5. `action`
6. `before_json`
7. `after_json`
8. `reason`
9. `request_id`
10. `client_ip`

## 6. 职责分离（SoD）
1. 同一用户不得同时拥有 `DISPATCHER + AUDITOR` 高危组合（默认禁止）。
2. 生产环境 `ADMIN` 不参与日常排程操作（默认禁止发布）。
3. 发布人与审批人不能是同一人（可配置强制）。

## 7. 验收标准
1. 关键接口全部具备后端鉴权。
2. 高风险操作全部具备审批链和审计链。
3. 越权调用有明确拒绝与日志记录。
4. 审计记录可按订单号、版本号、request_id追溯。
