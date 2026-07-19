# HANDOVER_LOCK 扩展实施总结：换届熔断扩展到立项（propose）

> 归档声明：本文仅记录历史分支和当时测试结果，不证明当前代码状态或业务规则正确性，不得作为新实现依据。

> **状态**：已落地（分支 `m3/handover-freeze-propose`，base = `main`）
> **PR**：待开
> **基线**：HANDOVER_LOCK 后 346 用例
> **本期**：累计 **351 用例，0F / 0E / 1S**（+5）
> **设计计划原文**：plan `declarative-cuddling-micali`（用户已批准）

---

## 1. 目标 & 边界回顾

上一期 HANDOVER_LOCK（查询派生换届熔断）只冻结了**财务公示发布**（`FinanceDisclosureApplicationService.lockAndPublish`）。换届选举进行期间，旧班子仍可通过 `POST /api/v1/voting-subjects`（`voting:subject:create`）**发起新的治理议题**，制造既成事实、留下治理空窗。

本期把同一个换届熔断器扩展到**立项（propose）**：**换届进行中冻结新 GENERAL/MAJOR 议题立项，换届结束自动恢复**。

### 关键约束（决定了"放行 ELECTION"）

`propose` 是**所有议题类型的唯一入口**。若一刀切冻结，会把"发起下一届 ELECTION 选举"也冻结，导致换届管理**自我死锁**。因此：**冻结 GENERAL/MAJOR 立项，放行 ELECTION 立项**。

| 维度 | 本期落地 |
|---|---|
| ✅ GENERAL/MAJOR 立项熔断 | `ProposalLifecycleService.propose` 落库前置闸门 |
| ✅ ELECTION 立项放行 | 显式跳过熔断检测，保证换届可正常发起 |
| ✅ 换届结束自动恢复 | 选举 SETTLED/CANCELLED → 查询返回空 → 熔断自动解除 |
| ✅ 复用既有检测 seam | `HandoverCircuitBreaker.activeElectionSubjectId`，无新查询、无迁移 |
| ❌ 按楼栋（BUILDING-scope）精细化熔断 | 租户级熔断，推迟 |

---

## 2. 落地清单

### 2.0 无 schema 迁移、无新查询（Flyway 仍 V3.2）

完全复用上期建好的检测设施：`HandoverCircuitBreaker`、域端口 `VotingSubjectRepository.findActiveElectionSubjectId`、mapper `selectActiveElectionSubjectId`（`subject_type=1 AND status IN (2,3,4)`）。本期不新增任何文件。

### 2.1 Application 层（注入熔断器 + propose 加闸）

- **`ProposalLifecycleService`** 用 `@RequiredArgsConstructor`，加 `private final HandoverCircuitBreaker handoverCircuitBreaker;` 即完成注入。
- **`propose`** 在 `subjectRepository.insert(draft)` **之前**、**仅对非 ELECTION** 插入熔断闸门：
  ```java
  // 换届熔断：换届选举在途期间冻结新 GENERAL/MAJOR 立项（放行 ELECTION，避免下一届换届自我死锁）
  if (cmd.subjectType() != SubjectType.ELECTION) {
      handoverCircuitBreaker.activeElectionSubjectId(cmd.tenantId()).ifPresent(electionId -> {
          throw new VotingApplicationException(
                  VotingApplicationException.Reason.PROPOSE_FROZEN_HANDOVER,
                  "换届选举进行中，新议题立项已熔断 electionSubjectId=" + electionId);
      });
  }
  ```
  镜像 `lockAndPublish` 的 `activeElectionSubjectId(...).ifPresent(...)` 守卫范式；不同点是**抛 `VotingApplicationException`**（propose 本就用该异常族）且**显式跳过 ELECTION**。
- **`VotingApplicationException.Reason`** 加常量 `PROPOSE_FROZEN_HANDOVER`。

### 2.2 Interfaces 层（错误码，零新增接线）

- **`ElectionErrorCode`** 加 `PROPOSE_FROZEN_HANDOVER(40926, "换届选举进行中，新议题立项已熔断", 409, ErrorType.BIZ, false)`——M3-2 议题生命周期 409xx 段下一个空号，不可重试（须待换届结束）。
- **`ElectionExceptionTranslator`** 的 `switch` 加 `case PROPOSE_FROZEN_HANDOVER -> ElectionErrorCode.PROPOSE_FROZEN_HANDOVER;`。
- `GlobalExceptionHandler` / `SubjectAdminController` / DTO **均不改**——复用既有 `POST /api/v1/voting-subjects`。

---

## 3. 测试矩阵（+5 用例）

| 测试类 | 风格 | 用例 | 覆盖 |
|---|---|---|---|
| `handover/ProposalHandoverGuardTest` | Mockito | 4 | 在途换届 + GENERAL → 抛 `PROPOSE_FROZEN_HANDOVER` 且**未**调 `insert`；MAJOR 同上；**ELECTION 放行**（熔断检测压根不触发）正常 `insert`；无换届 + GENERAL 正常 `insert` |
| `handover/ProposalHandoverEndToEndTest` | `@SpringBootTest`+MockMvc | 1 | 种 PUBLISHED ELECTION → `POST /voting-subjects`（GENERAL）→ `409 / 40926`；选举置 SETTLED → 同请求自动恢复 `201` |
| `voting/ProposalLifecycleServiceTest` | Mockito | —（适配） | 补 `@Mock HandoverCircuitBreaker` 以适配新构造器，既有 12 用例保持绿 |

> 种子隔离：guard 测试纯 Mockito 无库；e2e 用求是租户 `10001` + `HANDOVER-PROP-` 前缀，`@BeforeEach`/`@AfterEach` 按前缀清理，不触碰真实 seed。

```
mvn clean test
# Tests run: 351, Failures: 0, Errors: 0, Skipped: 1
# Flyway: schema 版本仍 3.2（本期无迁移）
# BUILD SUCCESS
```

| 阶段 | 累计 |
|---|---|
| HANDOVER_LOCK 后 | 346 |
| **换届熔断扩展到立项（本期 +5）** | **351** |

---

## 4. 提交节奏（仿前期三段，base=main）

```
m3/handover-freeze-propose                                                              ← 工作分支（base=main）
├── feat(handover): freeze GENERAL/MAJOR proposal during committee re-election          (Commit 1, 自洽可编译)
├── test(handover): proposal handover guard unit + e2e coverage                         (Commit 2, +5 用例)
└── docs(handover): extend HANDOVER_LOCK to proposal — implementation notes             (Commit 3, 本文档)
```

---

## 5. 已识别的剩余风险

1. **冻结粒度 = 租户级**：任一在途 ELECTION 冻结全租户 GENERAL/MAJOR 立项；BUILDING-scope 选举也会冻结社区级立项。与上期 disclosure 熔断"宁严勿松"语义一致；BUILDING-scope 精细化推迟。
2. **ELECTION 放行的副作用**：放行 ELECTION 立项意味着换届期可重复发起多条 ELECTION（如纠错重提）。这是有意为之——保证换届管理不自锁；多条在途 ELECTION 的治理合理性由上层流程/审批约束，不在本熔断器职责内。
3. **跨功能依赖方向合规**：`ProposalLifecycleService` 经 `HandoverCircuitBreaker` 间接依赖 `VotingSubjectRepository`（domain port），方向 application → domain，与 disclosure 服务的既有模式一致。
