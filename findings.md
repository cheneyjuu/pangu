# Findings & Decisions

## Requirements
- Review the current codebase.
- If issues are found, generate a remediation plan.
- Preserve user work and avoid unrelated code edits.

## Research Findings
- Repository root is clean at review start.
- Root modules visible: `pangu-bootstrap`, `pangu-interfaces`, `pangu-application`, `pangu-domain`, `pangu-infrastructure`.
- Parent POM defines Java 21, Spring Boot 3.2.5, MyBatis starter, Flyway, JJWT, Bouncy Castle, and Aliyun OSS dependency management.
- `docker-compose.yml` exposes PostgreSQL and Redis with default local credentials and public host ports.
- Source files indicate main review surfaces: Spring Security/JWT auth, tenant context interceptor, MyBatis data scope interceptor, gateway implementations, and voting domain engines.
- `SecurityConfig` permits every request; no JWT authentication filter establishes `SecurityContext`.
- `AuthService.login` signs JWT after `userGateway.getByPhone` only; `LoginRequest.smsCode` is not validated.
- `ElectionController` accepts `tenant_id` and `uid` from query parameters rather than deriving them from authenticated claims.
- `DataScopeInterceptor` is annotation-driven, but current Mapper interfaces do not use `@DataScope`; it also returns a hard-coded mock user context when Spring Security has no principal.
- `InfrastructureConfig` and `Sm4EncryptTypeHandler` print SM4 keys/cipher material to stdout.
- Existing integration tests assert login succeeds with only a phone number in the request body.
- Elevated `mvn test` passes locally with 8 tests, but depends on a running local PostgreSQL instance at `localhost:5432`; the sandboxed run failed before elevation with `Operation not permitted` while opening the DB socket.
- Test output confirms sensitive logging at runtime: SM4 key, ciphertext, SQL statements, SQL parameters, and generated JWT response bodies are printed.

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| Inspect module code before running expensive checks | Need to understand Maven structure and likely commands first. |

## Issues Encountered
| Issue | Resolution |
|-------|------------|
| Tests encode insecure login behavior | Record as CR finding and include remediation/tests in plan. |
| Sandboxed Maven test could not connect to local PostgreSQL | Re-ran with approved elevation; tests passed, proving the first failure was environment/sandbox connectivity. |

## Resources
- `/Users/juchen/Documents/workspace/pangu/pom.xml`
- `/Users/juchen/Documents/workspace/pangu/docker-compose.yml`
- `/Users/juchen/Documents/workspace/pangu/pangu-bootstrap/src/main/resources/application.yml`
- `/Users/juchen/Documents/workspace/pangu/pangu-interfaces/src/main/java/com/pangu/interfaces/web/config/SecurityConfig.java`

## Visual/Browser Findings
- None.
