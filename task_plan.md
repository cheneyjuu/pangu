# Task Plan: M1 RBAC 重写 (sys_permission + 七道触发器红线 + 三段式登录)

## Goal
按 `权限控制.md` / `权限矩阵.md` 把 Pangu 的身份/权限/数据隔离三件套重写：
permission-key 化的 RBAC、Postgres 触发器把红线沉到数据层、
三端模型 (G/B/S) + C 端业主分离、JWT 不带角色而是请求时实时 JOIN 出 UserContext。

## Scope
- Schema：V1.x 重写 + 新增 V1.4 sys_permission（不写 V0、不改既往迁移文件序号）
- Domain：`UserContext` / `UserContextHolder` / `UserContextLoader` / `DataScopeType` / `AuthenticationLevel`
- Infrastructure：`DefaultUserContextLoader` / `AccountMapper` / `UserContextMapper` / 拦截器改造
- Application：`WaiverApplicationService` / `VotingApplicationService` 改用 UserContext + Command 瘦身
- Interfaces：`JwtTokenProvider` / `JwtAuthenticationFilter` / `WaiverController` / 三段式 `AuthService`
- Tests：核心面 6 ~ 8 个测试类（trigger 反例 + @PreAuthorize + switch-tenant）

## Phases
| # | 内容 | Status |
|---|---|---|
| 15 | V1.x Schema 重写 | complete |
| 16 | Domain 层（DataScopeType / UserContext / 聚合根调整） | complete |
| 17 | Infrastructure 层（拦截器 / Loader / JWT / Mapper） | complete |
| 18 | Application 层（Service / Command 瘦身 / 三段式 login） | complete |
| 19 | Interfaces 层（Controller / DTO / @PreAuthorize） | complete |
| 20 | 现有测试适配 + 全量 mvn test 通过 | complete |
| 21 | Commit 2 — test(rbac) 矩阵 5 个测试类 / 22 用例 | complete |
| 22 | Commit 3 — docs + push + PR | in_progress |

## 关键决策
| 决策 | 理由 |
|---|---|
| 权限 = permission-key，角色只是聚合标签 | 后续 SaaS 管理员动态建角色不破坏权限模型 |
| @PreAuthorize 用 `hasAuthority('waiver:approve:committee')` | JWT 不缓存权限，UserContextLoader 实时 JOIN |
| 七道 Postgres 触发器写在 Schema 层 | 把"绝对不能违反"沉到 DB，不依赖应用代码 |
| trigger 2 用 DEFERRED 约束触发器 | 业主代表必须有楼栋绑定，但允许同事务先建用户 |
| `tenant_id` 三态：NULL = 跨租户根（街道办专用） | trigger 4 / 5 双向兼容业务 |
| 自然人三层：t_account 薄壳 + c_user / sys_user 双挂 | 一人一卡多端，phone 在 account，auth_level 在 c_user |
| DataScopeType: ALL_COMMUNITY / OWNER_GROUP / ORG_ONLY | 配合 fixed_data_scope，trigger 3 防越权 |
| AuthenticationLevel L1~L4 | L3 才允许 major decision，沿用 PRD 红线 |
| Commit 拆分：Commit 1 主重写 + Commit 2 RBAC 测试矩阵 + Commit 3 docs/PR | 主代码与测试矩阵分离，方便 reviewer 跟随触发器红线 |

## 验证清单
- `docker compose down -v && docker compose up -d` 重置 Postgres，让 V1.x 全量重放
- `mvn clean test` 必须 0F/0E（允许 1 Skipped）
- Commit 2 后 mvn test 计数：96 (上一阶段 74)
- 推送 + PR 之前由用户确认（destructive remote ops）
