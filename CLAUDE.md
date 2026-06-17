# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language

使用中文回答。

@AGENTS.md

## Architecture Overview

Pangu is a Java 21 / Spring Boot 3.2 multi-module backend for a community-governance / owner-autonomy SaaS. The five Maven modules form a one-direction dependency chain enforcing Clean Architecture:

```
pangu-bootstrap ──► pangu-interfaces ──► pangu-application ──► pangu-domain
                          │                                         ▲
                          └──► pangu-infrastructure ────────────────┘
```

`pangu-domain` is framework-light (no Spring/MyBatis imports) and exposes `Gateway` interfaces; `pangu-infrastructure` provides the `*GatewayImpl` adapters. Domain singletons (`AbacPolicyEngine`, `GeneralDecisionEngine`, `MajorDecisionEngine`, `ElectionVotingEngine`) are deliberately wired as Spring `@Bean`s in `pangu-bootstrap/.../config/DomainConfig.java` rather than annotated in the domain module — preserve that pattern when adding new domain services.

`PanguApplication` uses `scanBasePackages = "com.pangu"` and `@MapperScan("com.pangu.infrastructure.persistence.mapper")`; new mapper interfaces must live under that exact package or they will not be picked up.

## Voting & Settlement Engine

`AbstractVotingEngine<S, R>` (in `pangu-domain/.../model/voting/`) is a sealed template-method:

- `settle(...)` is `final` — it dedupes votes by `uid` and `uid+opid`, computes `participatingArea` and `participatingOwnerCount`, then checks the **dual 2/3 quorum** (专有面积 ≥ 2/3 AND 人数 ≥ 2/3) before delegating to `calculateResult(...)`.
- Concrete engines (`GeneralDecisionEngine`, `MajorDecisionEngine`, `ElectionVotingEngine`) only implement the strategy step — do not bypass `settle()` to add quorum logic of your own.
- One-household-multi-property and developer stock are merged at the `uid` level by design.

## ABAC + Multi-Tenancy

Two independent enforcement paths must stay consistent:

1. **Pre-action policy check** — `DefaultAbacPolicyEngine` (`pangu-domain/.../policy/impl/`) runs `evaluateCandidacy` and `evaluateVoting` based on a configurable `schemeType` (`SCHEME_A` strict / `SCHEME_B` permissive / `SCHEME_C` default-mixed) and `AuthenticationLevel` (L3 face-auth required for major decisions). Denials produce structured `EvaluationResult` consumed by `CandidacyRestrictedException`.
2. **Row-level data scope** — `DataScopeInterceptor` (`pangu-infrastructure/.../persistence/handler/`) is a MyBatis `StatementHandler` plugin. Any mapper method annotated with `@DataScope(buildingAlias=..., deptAlias=..., userAlias=...)` has its SQL parsed by jsqlparser and rewritten with an extra `WHERE` clause based on the user's `DataScopeType` (`ALL` / `OWN_DEPT_AND_CHILD` / `SELF` / `CUSTOM_BUILDING`). When parsing fails it falls back to the original SQL — do not rely on it as a hard security boundary; pair it with explicit checks for sensitive operations.

`TenantContext` (`pangu-domain/.../context/`) is a `ThreadLocal<Long>`. `TenantContextInterceptor` populates it per request; **always call `TenantContext.clear()` in `finally`** for any code path that sets it (background jobs, tests).

## Web / Error Contract

The error system was recently refactored — match the existing shape, don't reintroduce the deprecated `(int code, String message)` style for new code:

- `ErrorCode` is an interface with `code / message / httpStatus / errorType / needRetry`. Define new codes as enum constants on `CommonErrorCode` or a feature-scoped enum implementing `ErrorCode`.
- Throw `AppException(ErrorCode, ...)`; chain causes by passing a `Throwable cause` so `errorChain` and `needRetry` propagate from the root `AppException`.
- For domain-specific payloads (e.g. candidacy restriction details) extend `AppException` like `CandidacyRestrictedException` and surface the payload via `GlobalExceptionHandler` — that handler is the single funnel: it maps `httpStatus` onto the response, embeds `errorType` and `needRetry` into `Result.fail(...)`, and falls back to `CommonErrorCode.SYSTEM_ERROR` for unhandled exceptions.

## Security & Crypto

- JWT auth: `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter` (see `SecurityConfig`); only `/api/v1/auth/login` is public. Stateless session policy.
- Use `SecurityUtils` (in `pangu-interfaces/.../security/`) to read auth context — controllers/services should not call `SecurityContextHolder` directly (this convention is being enforced; recent commit `142300a` removed direct usages from `ElectionController`).
- Guomi SM4 is the data-at-rest cipher: `Sm4Util` + `Sm4EncryptTypeHandler` (a MyBatis `TypeHandler`) encrypt sensitive columns transparently — use the type handler in mapper XML rather than calling `Sm4Util` from services. Key is `platform.security.sm4-key-hex` in `application.yml`.

## Database & Migrations

- Postgres 15 via `docker compose up -d` (DB `pangu_db`, user/pass `postgres/password`); Redis 7 alongside.
- Flyway migrations under `pangu-bootstrap/src/main/resources/db/migration/` — current baseline is `V1`. Naming is `V<version>__<desc>.sql` (decimal versions like `V1.2` are in use). `clean-disabled: true` and `baseline-on-migrate: true` are set; never add `V0` or rewrite past versions.
- MyBatis: `map-underscore-to-camel-case: true`, mapper XMLs in `pangu-infrastructure/src/main/resources/mapper/`, type-aliases under `com.pangu.infrastructure.persistence.entity`.

## Testing

All tests live in `pangu-bootstrap/src/test/java`. Existing suites cover the patterns to mirror:

- `voting/VotingDecisionEngineTest`, `voting/ElectionVotingEngineTest` — pure-domain quorum/settlement scenarios.
- `persistence/DataScopeTest` — exercises the SQL-rewrite interceptor end-to-end.
- `web/ControllerIntegrationTest`, `web/AppExceptionBehaviorTest` — full Spring Boot Test boot for controller + global-handler behavior.

Run a single test class:

```
mvn -pl pangu-bootstrap -am test -Dtest=ElectionVotingEngineTest
```

When changing voting rules, add a domain test against the engine **before** modifying `calculateResult`. When changing the SQL rewrite, extend `DataScopeTest` rather than adding ad-hoc SQL assertions elsewhere.

## Conventions Worth Repeating

- 4-space indent; explicit imports (no `import x.y.*` and no fully-qualified names in code — see commit `a245dad`).
- Lombok is on the classpath project-wide (`scope=provided` in parent POM); use it consistently with surrounding files, but keep `pangu-domain` minimal in annotations.
- Conventional Commits with module/feature scope: `feat(auth): ...`, `refact(web): ...`, `style(election): ...`.
