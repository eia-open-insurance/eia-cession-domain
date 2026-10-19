# eia-cession-domain

- GitHub organization: `eia-open-insurance`
- Repository URL: https://github.com/eia-open-insurance/eia-cession-domain
- Type: Spring Boot service
- Deployable: Yes
- Owner: Cession
- Runtime: Java 22 and Spring Boot
- Data store: eia_cession (tx schema)
- Depends on: eia-cession-expert, eia-contracts, eia-platform/libs

## Purpose

Cession business rules, transaction catalog, orchestration, approvals, and APIs. Direct-book ceded reinsurance contracts, cession rules, FS-RI hand-off, and recoverables.

## Responsibilities

- Keep this repository focused on one deployable component or one platform concern.
- Follow the repository standard in `../CONVENTIONS.md` and the specification in `../../SPEC.md`.
- Publish and consume contracts through `eia-contracts`.
- Maintain README, CODEOWNERS, SECURITY, CHANGELOG, tests, and local verification.
- Contain no business logic in shared libraries or front-end remotes.

## Verification

- Lint and format
- Unit tests with 100% business-logic coverage where applicable
- Contract tests
- Integration or Playwright tests where applicable
- Security scan and SBOM
- Build and publish

## Implemented slice

Spring Boot 3.4.0, Java 22, Gradle Kotlin DSL, port `8092`. Calls the expert through a `RestClient`
component at `eia.cession.expert-url` (default `http://localhost:8091`).

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/cessions` | List cessions for the caller's tenant |
| `GET` | `/api/v1/cessions/{id}` | Read one cession |
| `POST` | `/api/v1/cessions` | Create a cession in `OPEN` |
| `POST` | `/api/v1/cessions/{id}/transition` | Apply a lifecycle transition |

Every request requires `X-EIA-Tenant: <uuid>` and `X-EIA-Book: DIRECT`. A missing or non-`DIRECT`
book returns a structured `400` (`BOOK_SCOPE_VIOLATION`); unknown records return a structured
`404`. Amounts are validated as non-negative before the expert is called and the cession lifecycle
is enforced in the domain (`INVALID_STATUS_TRANSITION`). The web remote relies on the `/api` proxy
at `/api/v1/cessions`.

## Local run

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-22.0.2.9-hotspot'
.\gradlew.bat build
.\gradlew.bat bootRun
```

Start the expert first. Health: `http://localhost:8092/actuator/health`.

## Notes

Owns the business logic for this vertical.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
