# Changelog

All notable changes to this repository are documented here.

The format is based on Keep a Changelog and the project uses Semantic Versioning.

## [Unreleased]

### Added

- Repository baseline.
- Spring Boot 3.4.0 / Java 22 domain service on port 8092 with Gradle Kotlin DSL.
- `/api/v1/cessions` list, get, create, and transition endpoints with `X-EIA-Tenant` and `X-EIA-Book` guards.
- `GET /api/v1/cessions/search` shell global-search projection with the common
  `query`/`limit`/`items[]` shape, tenant scoping, and the DIRECT book wall.
- `RestClient` integration with the cession expert.
- DIRECT-book wall, non-negative amount, and SPEC 11.9.3 lifecycle validation with structured 400/404 errors.
- JUnit controller and service tests.
