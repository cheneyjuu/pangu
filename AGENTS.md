# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21, Spring Boot 3.2 multi-module Maven backend for the Pangu SaaS platform. Source code lives under each module's `src/main/java/com/pangu/...`; tests currently live in `pangu-bootstrap/src/test/java`.

- `pangu-domain`: pure domain models, policies, voting engines, and gateway interfaces.
- `pangu-application`: use-case/application services; depends on domain.
- `pangu-infrastructure`: MyBatis mappers/XML, persistence adapters, Redis, OSS, crypto, and interceptors.
- `pangu-interfaces`: REST controllers, DTOs, security filters, and web configuration.
- `pangu-bootstrap`: `PanguApplication`, Spring wiring, `application.yml`, Flyway migrations in `src/main/resources/db/migration`.

## Build, Test, and Development Commands

- `docker compose up -d`: starts local PostgreSQL (`pangu_db`) and Redis.
- `mvn clean test`: runs the full multi-module test suite.
- `mvn clean package`: builds all modules and repackages the Spring Boot app.
- `mvn -pl pangu-bootstrap -am spring-boot:run`: runs the API locally on `http://localhost:8080/pangu`.
- `mvn -pl pangu-domain -am test`: runs a focused module test build with required upstream modules.

## Coding Style & Naming Conventions

Use 4-space indentation, Java 21 language features conservatively, and package names under `com.pangu`. Keep domain code framework-light; Spring, MyBatis, Redis, and external service details belong outside `pangu-domain`. Follow existing naming: controllers end with `Controller`, services with `Service`, gateways with `Gateway` or `GatewayImpl`, MyBatis mappers with `Mapper`, and Flyway files as `V<version>__description.sql`.

Prefer explicit imports over fully qualified names. Lombok is available; use it consistently with nearby code.

## Testing Guidelines

Tests use JUnit 5 and Spring Boot Test. Name test classes `*Test` and place integration-heavy tests in `pangu-bootstrap/src/test/java`. Add domain behavior tests for voting, policy, and model logic before changing rules. For persistence or web changes, include coverage that exercises Flyway schema, MyBatis mappings, tenant/data-scope behavior, or controller responses as applicable.

## Commit & Pull Request Guidelines

Git history follows Conventional Commit style: `feat(auth): ...`, `style(election): ...`, `refact(election): ...`. Keep scopes short and module or feature oriented.

Pull requests should include the problem, solution summary, affected modules, test commands run, and any database migration notes. Include screenshots or API examples when changing HTTP behavior.

## Security & Configuration Tips

`application.yml` contains development defaults only. Do not commit production JWT secrets, SM4 keys, OSS credentials, or database passwords. Prefer environment-specific overrides for deploys.

## Agent-Specific Instructions

Before agent-driven changes, run `~/.codex/superpowers/.codex/superpowers-codex bootstrap` and follow the returned workflow.
