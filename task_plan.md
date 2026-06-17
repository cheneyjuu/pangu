# Task Plan: Error Code and Exception System Optimization

## Goal
Optimize Pangu's API error code and exception system with clear semantics, consistent response metadata, and regression tests.

## Scope
- `pangu-interfaces/src/main/java/com/pangu/interfaces/web/controller/*ErrorCode*.java`
- `pangu-interfaces/src/main/java/com/pangu/interfaces/web/controller/*Exception*.java`
- `pangu-interfaces/src/main/java/com/pangu/interfaces/web/controller/GlobalExceptionHandler.java`
- Focused tests under `pangu-bootstrap/src/test/java`

## Phases
1. Inspect current exception model and usages - complete
2. Write failing tests for expected behavior - complete
3. Refactor implementation compatibly - complete
4. Run targeted Maven tests - complete
5. Summarize changes and risks - complete

## Decisions
| Decision | Rationale |
|---|---|
| Preserve existing response shape | Controllers/tests already use `Result` with `code`, `msg`, `errorType`, and `needRetry`. |
| Keep changes in interfaces layer initially | Existing exception classes live under web controller package. |
| Use tests before production edits | Required for behavior-affecting refactor. |

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| Target test pattern failed in upstream modules without matching tests | 1 | Added `-Dsurefire.failIfNoSpecifiedTests=false` for focused test run. |
| Full `mvn test` failed in sandbox while connecting to local PostgreSQL | 1 | Verified containers were running, then re-ran `mvn test` outside sandbox with local network access. |
