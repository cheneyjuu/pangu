# HANDOVER_LOCK 实施总结：业委会换届熔断（查询派生，零迁移）

> 归档声明：本文仅记录历史分支和当时测试结果，不证明当前代码状态或业务规则正确性，不得作为新实现依据。

> **状态**：已落地（分支 `m3/handover-lock`，base = `main`）
> **PR**：待开
> **基线**：M3-3 后 316 用例
> **本期**：累计 **331 用例，0F / 0E / 1S**（+15）
> **设计计划原文**：plan `declarative-cuddling-micali`（一对一确认完毕）

---

## 1. 目标 & 边界回顾

M3-3 把 ELECTION 业委会换届选举全流程（立项 → 提名 → 资格审查 → 公示 → 开票 → 投票 → 结算）接通后，暴露出一个**治理空窗**：换届选举进行期间（旧业委会即将卸任、新业委会尚未产生），旧班子仍可发起敏感治理动作——最典型的是**发布财务公示**（`disclosure:publish`，业委会主任权限）。

本期引入「换届熔断 HANDOVER_LOCK」：**换届进行中冻结财务公示发布，换届结束自动恢复**。

### 关键约束发现（决定了"查询派生"方案）

1. **不存在「届 / 委员会」实体**——全代码库无 `t_committee`、无 term/届 概念。"换届进行中" 无法挂在某条委员会记录上，只能表达为「该租户存在一个处于公示/投票/待结算的 ELECTION 议题」。
2. **M2-1 `t_governance_lock` 是手工双签解锁模型**（trigger 8 强制 `FULLY_UNLOCKED` 须业委会主任 + 街道办双签），与换届「选举结算后自动释放」**根本冲突**。复用该表会污染其不可逆双签语义。

| 维度 | 本期落地 |
|---|---|
| ✅ 换届进行中检测 | 查询派生：`VotingSubjectRepository.findActiveElectionSubjectId` |
| ✅ 财务公示发布熔断 | `FinanceDisclosureApplicationService.lockAndPublish` 前置闸门 |
| ✅ 换届结束自动恢复 | 选举 SETTLED/CANCELLED → 查询返回空 → 熔断自动解除 |
| ❌ 立项（propose）熔断 | 本期不冻结（仅财务公示发布） |
| ❌ 锁状态表 / engage-release 记账 / 人工 override | 不建表，查询派生 |
| ❌ 按楼栋（BUILDING-scope）精细化熔断 | 租户级熔断，推迟 |

### 已锁定的设计决策（一对一确认）

1. **锁状态载体 = 查询派生**：不建表、不写锁行、不做 engage/release 记账。"换届进行中" 直接由 ELECTION 议题状态推导，自动一致、自动恢复、零迁移。复用的是 governance_lock 的「动作前 verify 守卫」**范式**（而非其表）。
2. **冻结范围 = 仅财务公示发布**（`FinanceDisclosureApplicationService.lockAndPublish`）。换届期发起新治理议题（propose）本期**不**冻结。

---

## 2. 落地清单

### 2.0 无 schema 迁移（Flyway 仍 V3.1）

查询派生用现有列 `t_voting_subject.{tenant_id, subject_type, status}`，无新表、无新索引（发布财务公示低频，无需 `(tenant_id, subject_type, status)` 索引；如后续需要再评估，本期不加，避免 V3.2）。

### 核心语义

- **换届进行中** ≙ 该租户存在 `subject_type = ELECTION(1)` 且 `status ∈ {PUBLISHED(2), VOTING(3), CLOSED(4)}` 的议题。
  - 含 `CLOSED(4)`：投票已截止但未结算时，新业委会尚未产生，熔断应持续到 `SETTLED`。
  - 不含 `DRAFT(1)`：仅草拟、未公示，尚未进入换届公示期。
  - `SETTLED(5)` / `CANCELLED(6)` → 查询自然返回空 → 自动解除熔断。
- **范围 = 租户级**：任一在途 ELECTION 即冻结全租户财务公示发布。

### 2.1 Domain 层（检测 seam，框架轻量）

- **`VotingSubjectRepository`** 加端口方法 `Optional<Long> findActiveElectionSubjectId(Long tenantId)`——「换届进行中」的唯一判定来源，纯接口、无 Spring/MyBatis。未来若扩冻结范围（如 propose），复用同一方法。

### 2.2 Infrastructure 层

- **`VotingSubjectMapper`(.java/.xml)** 加 `selectActiveElectionSubjectId`：
  ```sql
  SELECT subject_id FROM t_voting_subject
   WHERE tenant_id = #{tenantId} AND subject_type = 1 AND status IN (2, 3, 4)
   ORDER BY subject_id ASC LIMIT 1
  ```
- **`VotingSubjectRepositoryImpl`** 实现 `findActiveElectionSubjectId`（`Optional.ofNullable(mapper.selectActiveElectionSubjectId(tenantId))`）。

### 2.3 Application 层（熔断器 + 消费）

- **新增 `HandoverCircuitBreaker`**（`pangu-application/.../handover/`，`@Service`）——给"换届熔断"一个具名归处，集中"在途 = status∈{2,3,4}"语义与未来扩展点；仅依赖 `VotingSubjectRepository`（domain port），不耦合任何具体功能的异常类型：
  ```java
  public Optional<Long> activeElectionSubjectId(Long tenantId) {
      return subjectRepository.findActiveElectionSubjectId(tenantId);
  }
  ```
- **`FinanceDisclosureApplicationService.lockAndPublish`** —— 在 `lockApplicationService.lock(...)` **之前**（tenant 一致性校验之后）插入熔断闸门：
  ```java
  handoverCircuitBreaker.activeElectionSubjectId(cmd.tenantId()).ifPresent(electionId -> {
      throw new FinanceDisclosureApplicationException(
          FinanceDisclosureApplicationException.Reason.HANDOVER_IN_PROGRESS,
          "换届选举进行中，财务公示发布已熔断 electionSubjectId=" + electionId);
  });
  ```
  构造器注入 `HandoverCircuitBreaker`（与现有 `GovernanceLockApplicationService` 注入并列）。
- **`FinanceDisclosureApplicationException.Reason`** 加常量 `HANDOVER_IN_PROGRESS`。

> 选择「抛 disclosure 自己的异常」而非新建 handover 异常族：本期仅 disclosure 一处冻结，复用既有 `@ExceptionHandler(FinanceDisclosureApplicationException)`，零新增接线。熔断**检测**已抽到 `HandoverCircuitBreaker`，未来加 propose 冻结时各功能抛各自异常即可。

### 2.4 Interfaces 层（错误码，零新增接线）

- **`DisclosureErrorCode`** 加 `HANDOVER_IN_PROGRESS(41109, "换届选举进行中，财务公示发布已熔断", 409, ErrorType.BIZ, false)`——411xx 段下一个空号，不可重试（业主/主任需待换届结束）。
- **`DisclosureExceptionTranslator.translate`** 的 `switch` 加 `case HANDOVER_IN_PROGRESS -> DisclosureErrorCode.HANDOVER_IN_PROGRESS;`。
- `GlobalExceptionHandler` / `FinanceDisclosureController` / DTO **均不改**——复用既有 `POST /api/v1/disclosures/{snapshotId}/publish`（`disclosure:publish`）。

---

## 3. 测试矩阵（`pangu-bootstrap/src/test/java/.../handover/`，+15 用例）

| 测试类 | 风格 | 用例 | 覆盖 |
|---|---|---|---|
| `HandoverDetectionTest` | `@SpringBootTest`+JdbcTemplate | 9 | ELECTION PUBLISHED/VOTING/CLOSED → 命中；DRAFT/SETTLED/CANCELLED → 空；非 ELECTION（GENERAL/MAJOR）VOTING → 空；跨租户隔离 |
| `HandoverCircuitBreakerTest` | Mockito | 2 | `activeElectionSubjectId` 对仓储结果的命中/空透传 |
| `FinanceDisclosureHandoverGuardTest` | Mockito | 2 | `lockAndPublish`：breaker 命中 → 抛 `HANDOVER_IN_PROGRESS` 且**未**调 `lockApplicationService.lock`；breaker 空 → 正常推进锁 + 发布 |
| `DisclosureHandoverEndToEndTest` | `@SpringBootTest`+MockMvc | 1 | 种 PUBLISHED ELECTION → `POST /disclosures/{id}/publish` → `409 / 41109`，快照仍 DRAFT；选举置 SETTLED → 再次 publish → `200`（自动恢复，快照 PUBLISHED） |
| `DisclosureExceptionTranslatorTest` | 纯单测 | 1 | `HANDOVER_IN_PROGRESS` → `DisclosureErrorCode.HANDOVER_IN_PROGRESS`（41109/409/不可重试） |

> 种子隔离：检测测试用独立租户 `95001/95002` + `HANDOVER-DETECT-` 前缀；e2e 把 DRAFT 快照直插到求是租户 `10001` 的专用 period `2099-12`（周主任持 `disclosure:publish`），换届选举用 `HANDOVER-E2E-` 前缀，精准清理不触碰真实 seed。

```
mvn clean test
# Tests run: 331, Failures: 0, Errors: 0, Skipped: 1
# Flyway: schema 版本仍 3.1（本期无迁移）
# BUILD SUCCESS
```

| 阶段 | 累计 |
|---|---|
| M3-3 后 | 316 |
| **HANDOVER_LOCK（本期 +15）** | **331** |

---

## 4. 提交节奏（仿 M3-3 三段，base=main）

```
m3/handover-lock                                                                       ← 工作分支（base=main）
├── feat(handover): freeze finance-disclosure publish during committee re-election     (Commit 1, 自洽可编译)
├── test(handover): handover detection + circuit-breaker guard + disclosure e2e        (Commit 2, +15 用例)
└── docs(handover): HANDOVER_LOCK implementation notes                                 (Commit 3, 本文档)
```

---

## 5. 已识别的剩余风险

1. **冻结粒度 = 租户级**：任一在途 ELECTION 冻结全租户财务公示，BUILDING-scope 选举也会冻结社区级公示。当前业务下换届是社区级事件，租户级熔断符合"宁严勿松"的熔断器语义；若未来要求按楼栋精细化，需把检测查询与 disclosure 的 scope 对齐，本期记录推迟。
2. **查询派生 = 无审计行 / 无人工 override**：不留"何时熔断"的独立记录，也无"争议换届期间紧急解冻"通道——这是放弃建表换来的简洁与自动一致性，符合本次"查询派生"决策。若后续需要审计/override，再升级为 V3.2 专用表（已评估，本期不做）。
3. **状态集合边界**：在途集合取 `{PUBLISHED, VOTING, CLOSED}`，依赖 ELECTION 必经 SETTLED/CANCELLED 终结。若存在选举长期滞留 CLOSED 不结算的运维异常，会持续冻结发布——这本身是正确的熔断行为，但需运维层面保证选举及时结算。
4. **跨功能依赖**：`FinanceDisclosureApplicationService` 经 `HandoverCircuitBreaker` 间接依赖 `VotingSubjectRepository`。属 domain port 依赖，方向合规（application → domain），与该服务已依赖 `GovernanceLockApplicationService` 的跨功能模式一致。
5. **TOCTOU 窗口**：熔断检测与治理锁之间存在极小时间窗——检测通过后、发布事务提交前，恰好有一条 ELECTION 被公示。属低频运维竞态，且发布最终仍受治理锁串行化保护，本期不加额外约束。
