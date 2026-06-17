# Findings: Error Code and Exception System

## Current Model
- `ErrorCode` exposes `code`, `message`, `httpStatus`, `errorType`, and `needRetry`.
- `CommonErrorCode` stores error type as raw `String` values such as `BIZ` and `SYSTEM`.
- `AppException` preserves an `errorChain` and inherits retry behavior from nested `AppException` causes.
- `GlobalExceptionHandler` maps `AppException` to `Result.fail(...)` and copies candidacy restriction details into `data`.

## Improvement Targets
- Add a type-safe error category to prevent string drift while keeping `getErrorType()` compatible.
- Centralize `AppException` initialization to reduce duplicated constructor logic.
- Validate null `ErrorCode` inputs with a clear failure instead of late `NullPointerException`.
- Keep formatted message fallback behavior for bad `String.format` patterns.
- Fix deprecated `AppException(int, String)` so arbitrary business codes do not become invalid HTTP status codes.
- Add a fallback handler for uncaught exceptions so all API errors share the same response envelope.

## Constraints
- Preserve existing `Result` response fields.
- Preserve public constructor signatures where possible.
- Keep changes focused to the interfaces/web exception layer plus tests.
