# SwiftPay — Money Transfer Service (Backend)

Instant money transfer backend for SwiftPay, a digital bank. Built with Java 21 and Spring Boot.

## Tech stack

- Java 21, Spring Boot 3.3.5 (Web, Data JPA, Validation, Security, Actuator)
- PostgreSQL 16 (relational storage), Flyway for schema migrations
- JWT (jjwt) for stateless authentication, BCrypt for password hashing
- JUnit 5 + Mockito (unit tests), Spring Boot Test + MockMvc (integration tests), JaCoCo (coverage)
- Maven

## Design overview

- **Entities**: `Customer` (holds per-transfer/daily/monthly limits — configurable per customer, seeded with the
  defaults below), `Account` (balance, optimistic-locked via `@Version`), `Transfer` (status machine:
  `PENDING → COMPLETED/FAILED`).
- **Idempotency**: every transfer request carries a client-supplied `idempotencyKey`, stored as a unique column.
  If the same key is submitted again, the previously persisted transfer is returned unchanged — no funds move
  twice, and this works even under concurrent duplicate requests because the database enforces uniqueness.
- **Transfer flow** (`TransferService`, single `@Transactional` method): idempotency check → ownership check
  (source account must belong to the caller) → destination IBAN resolution (must exist in this system) →
  self-transfer rejection → per-transfer / daily / monthly limit checks (only `COMPLETED` transfers count,
  computed on the fly from the `transfers` table) → balance check → debit/credit both accounts (optimistic
  locking via JPA `@Version`; a concurrent conflicting update surfaces as `409 TRANSFER_CONFLICT`) → persist as
  `COMPLETED`.
- **Security**: stateless JWT auth (`Authorization: Bearer <token>`), obtained via `POST /api/auth/login`.
  Passwords are BCrypt-hashed. Every account/transfer lookup by id verifies the resource belongs to the
  authenticated customer (IDOR protection) — otherwise `403 ACCESS_DENIED` / `404 RESOURCE_NOT_FOUND`. All
  inputs are validated with `jakarta.validation`. All queries use JPA/parameterized SQL (no string-concatenated
  SQL), so there's no SQL injection surface.
- **Error model**: every error response is `{errorCode, message, traceId, timestamp}` (see
  `com.swiftpay.dto.ApiErrorResponse`), produced centrally by `GlobalExceptionHandler`. `traceId` is a random
  UUID logged server-side alongside the error, for correlation.
- **Statistics**: `GET /api/statistics/monthly` returns exactly 6 rows (current month + previous 5), newest
  first, zero-filled for months with no completed transfers — computed by combining a single aggregate SQL
  query (grouped by month, `COMPLETED` only) with in-memory zero-filling for the full window.

## Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/login` | Authenticate, returns JWT |
| POST | `/api/transfers` | Create a transfer (idempotent) |
| GET | `/api/transfers/{id}` | Get a transfer by id (own transfers only) |
| GET | `/api/transfers?startDate=&endDate=` | List own transfers, optionally by date range (ISO-8601 instants) |
| GET | `/api/accounts/me` | List own accounts with balances |
| GET | `/api/accounts/me/limits` | Remaining daily/monthly limits |
| GET | `/api/statistics/monthly` | Last 6 months of completed-transfer stats |

A ready-to-import Postman collection is at `postman/SwiftPay.postman_collection.json`.

## Running it

PostgreSQL 16 must be running locally with two databases: `swiftpay` (app) and `swiftpay_test` (integration
tests), owned by role `swiftpay`/`swiftpay`. In this container that was already set up with:

```bash
service postgresql start
sudo -u postgres psql -c "CREATE USER swiftpay WITH PASSWORD 'swiftpay' CREATEDB;"
sudo -u postgres psql -c "CREATE DATABASE swiftpay OWNER swiftpay;"
sudo -u postgres psql -c "CREATE DATABASE swiftpay_test OWNER swiftpay;"
```

Start the app (Flyway migrates the schema and seeds demo data automatically):

```bash
mvn spring-boot:run
```

API listens on `http://localhost:8080`.

Run the test suite (unit + integration, against `swiftpay_test`):

```bash
mvn test
```

JaCoCo report: `target/site/jacoco/index.html`. **Backend test coverage: 99.4% instruction coverage** (89
unit/integration tests; see `target/site/jacoco/jacoco.csv` for the authoritative, regenerated-on-every-run
number — well above the 80% requirement). The only meaningfully uncovered lines are `SwiftpayApplication.main`
and a couple of defensive branches that cannot be hit without mocking a JVM-level failure.

A live, scenario-level smoke test (success paths, every error path, limit boundaries, malicious/extreme
inputs, HTTP-semantics edge cases, and a 50-concurrent-request double-spend check) is at
`scenario-tests/run_scenarios.sh` — run it against a running instance (`mvn spring-boot:run`) with
`bash scenario-tests/run_scenarios.sh`. It currently runs **58 scenarios, all passing**.

## Demo credentials (seeded by `V2__seed.sql`)

| Username | Password | IBAN | Starting balance |
|---|---|---|---|
| `alice` | `Passw0rd!` | `TR180006200000000011110001` | 200,000.00 TRY |
| `bob` | `Passw0rd!` | `TR660006200000000022220002` | 50,000.00 TRY |

## Configuration

Limits are configurable in `application.yml` under `swiftpay.limits` (used as the seeded default for new
customers; each customer's *live, enforced* limits are columns on `customers`, so they can also be changed per
customer without a redeploy or code change — `LimitsController`-less by design, change the row). These default
values are validated at application startup (`BusinessProperties.validate()`, fail-fast): all must be positive,
and `daily-total >= per-transfer` and `monthly-total >= daily-total`, or the app refuses to start. The same DB
invariants are also enforced as CHECK constraints (`V3__constraints.sql`) as defense in depth.

The day/month boundaries used for daily/monthly limit resets are computed in `swiftpay.business.zone-id`
(default `Europe/Istanbul`), not the server's own timezone — see Assumptions.

JWT secret/expiry and the CORS allowlist are under `swiftpay.security`.

## Extra test coverage added during an exhaustive scenario-checklist audit

Beyond the original unit/integration/scenario suites, a full pass against an ~200-item, 18-category test
checklist (authentication, authorization/IDOR, validation, balance rules, limits, idempotency, concurrency,
error model, security/injection, data precision) was carried out against this codebase and a live running
instance. The following real gaps were found and fixed, each backed by a new test:

| Fix | Where tested |
|---|---|
| IBAN format + mod-97 checksum validation (previously only length was checked) | `ValidIbanValidatorTest` (unit), scenario: lowercase/spaces/Cyrillic-lookalike/bad-checksum IBAN cases |
| Amount scale validation: reject more than 2 decimal places, reject absurdly large (overflowing) amounts as 400 instead of risking a DB overflow | `TransferRequest` `@Digits`; scenario: "absurdly large amount", existing `VA-03`-style unit tests |
| Idempotency key reused with a **different** request body now returns `409 TRANSFER_CONFLICT` instead of silently replaying the old result | `TransferServiceTest.createTransfer_sameIdempotencyKeyDifferentAmount_throwsConflict`; scenario: "ID-02" |
| Idempotency key format/length validation (`@Pattern`, min 8 chars) | scenario: "ID-04" |
| `startDate > endDate` on the list/date-range endpoint now returns `400 VALIDATION_ERROR` instead of silently returning an empty list | `TransferServiceTest.listTransfers_startAfterEnd_throwsValidationException`; scenario: "endDate before startDate" |
| Wrong `Content-Type` → `415`, wrong HTTP method → `405`, oversized request body → `413` (all previously fell through to a generic `500`) | `GlobalExceptionHandlerTest` (3 new tests), `MaxBodySizeFilterTest`; scenario: VA-51/VA-55/VA-54 |
| Day/month limit-boundary calculations moved from hardcoded UTC to a configurable timezone (`Europe/Istanbul` default) | `BusinessPropertiesTest`; existing limit-boundary unit + scenario tests re-verified against the new zone |
| Limit-default configuration is now validated at startup (fail fast) instead of being silently accepted if nonsensical | `BusinessPropertiesTest` (5 cases: valid, bad zone id, negative, daily<per-transfer, monthly<daily) |
| Description field rejects raw control characters (defends against log injection via CRLF/control chars) | covered by existing `@Pattern` validation tests on `TransferRequest` |
| Explicit regression test proving a receiver never sees a transfer in their own outgoing list (see Assumptions → "incoming transfers") | scenario: "AZ-04/LT-13 receiver's own transfer list..." |

Two checksum-invalid demo IBANs in the original seed data (`V2__seed.sql`) were also corrected to valid
mod-97 checksums (`TR180006200000000011110001` / `TR660006200000000022220002`) now that IBAN validation is
real; every test, the Postman collection and the scenario script were updated to match.

## Security

- **Authentication**: stateless JWT (`io.jsonwebtoken`, HS256), issued by `POST /api/auth/login`, required on
  every other endpoint. A missing/invalid/expired token returns `401 AUTH_ERROR` with the standard error body
  (not an empty response — this was a real bug found and fixed during review: the Spring Security entry point
  used to call `response.sendError()` directly, bypassing `GlobalExceptionHandler`).
- **Password storage**: BCrypt (`BCryptPasswordEncoder`, default cost factor 10).
- **Timing-attack resistant login**: whether the username exists or not, `AuthService.login` always performs a
  BCrypt comparison (against a fixed dummy hash when the user is unknown) before rejecting, so response time
  cannot be used to enumerate valid usernames.
- **Brute-force protection**: `LoginRateLimiter` blocks an IP+username pair after 5 failed attempts within a
  60-second window, returning `429 TOO_MANY_ATTEMPTS`. It is in-memory and per-instance — sufficient for a
  single-node deployment; a multi-instance deployment would need a shared store (e.g. Redis) instead.
- **IDOR protection**: the authenticated customer id is taken only from the verified JWT claim
  (`CurrentCustomer.id()`, backed by `SecurityContextHolder`) — never from a client-supplied id/parameter.
  Every account/transfer lookup checks ownership and returns `403 ACCESS_DENIED` otherwise. Verified both by
  unit/integration tests and by the live scenario suite.
- **CORS**: locked to an explicit origin allowlist (`swiftpay.security.allowed-origins`, defaults to the local
  frontend dev origins), not a wildcard — credentials are allowed, so a wildcard origin would be rejected by
  browsers anyway, but the explicit allowlist also blocks arbitrary origins at the preflight.
- **No information leakage in error responses**: unexpected exceptions return a generic
  `500 INTERNAL_ERROR` with no stack trace, SQL text, or internal class/package names in the body; the full
  exception is only written to the server log, keyed by the same `traceId` returned to the client.
- **SQL injection**: all queries are JPA-derived or `@Param`-bound JPQL/native queries — no string
  concatenation with user input anywhere in the repository layer. IBAN input is additionally rejected by format
  validation (`@ValidIban`) before it can ever reach a query at all.
- **Log injection**: `description` rejects raw control characters (including CR/LF), so a crafted description
  can never forge an extra log line.
- **HTTP semantics are never a 500**: wrong `Content-Type` → `415`, wrong HTTP method → `405`, oversized body →
  `413`, malformed JSON/UUID/dates → `400` — all via the standard error body, never an unhandled exception.
- **Mass assignment**: request DTOs expose only the fields a client should set (e.g. `TransferRequest` has no
  `customerId`/`status`/`id` field a caller could use to override server-assigned values).
- **Security headers**: Spring Security's defaults are enabled (`X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, cache-control on sensitive responses).

Known, accepted limitation: the rate limiter and the idempotency/limit-check-then-write sequence are correct
for a single application instance; a horizontally-scaled deployment would need a shared rate-limit store and
(for the small theoretical read-then-write race noted below) a stricter locking strategy.

## Assumptions

Every subjective/design decision surfaced by the scenario-checklist audit is written down here, with the
behavior it produces and how it's verified — not just noted, but implemented and tested.

- **Single currency, single system**: all accounts are TRY and transfers only move money between two accounts
  that already exist in SwiftPay's own database (resolved by IBAN) — no external bank network, no FX. There is
  therefore no currency-mismatch code path to test (the only currency that can ever appear is TRY).
- **IBAN scope**: this system only issues/accepts Turkish IBANs. `destinationIban` must be exactly 26
  characters, start with `TR`, use uppercase ASCII digits/letters only (no spaces, dashes, lowercase, or
  Unicode look-alikes), and pass the real mod-97 checksum — all enforced by `@ValidIban` before any database
  lookup happens. A syntactically valid IBAN that simply isn't registered in this system returns
  `422 DESTINATION_NOT_FOUND`; a malformed one returns `400 VALIDATION_ERROR`. IBANs are **not masked** in API
  responses (a transfer always shows the full destination IBAN) — this is a backend API for the account owner
  themselves, not a counterparty-facing statement, and the case study does not ask for masking; a UI layer is
  free to mask for display.
- **No customer registration/KYC endpoint**: customers and accounts are seeded directly via Flyway, since
  registration/KYC was explicitly out of scope. Consequently there is no "closed/blocked/frozen account" state
  either — every seeded account is always active; this is a modeling simplification, not an oversight.
- **Limits are per-customer, not per-account**: a customer with multiple accounts shares one daily/monthly cap
  across all their accounts (debits only). Incoming transfers never count against anyone's limit — a limit caps
  how much a customer can *send*, and the receiving side of a transfer is a different customer's account,
  decoupled from the sender's limit bookkeeping entirely.
- **A transfer is only ever visible to its sender, not its receiver**: `GET /api/transfers` and
  `GET /api/transfers/{id}` are scoped to `transfers.customer_id`, which is always the *sender*. Bob never sees
  a transfer Alice sent him in his own transfer list, detail view, or monthly statistics — only in his account
  balance, which updates immediately. This is a deliberate, tested design choice (see the "AZ-04/LT-13" scenario
  test), not an accident: it mirrors "my outgoing transfer history," and a statement of incoming funds would be
  a different, not-yet-specified endpoint.
- **Limit-violation priority is deterministic and intentionally ordered**: per-transfer limit is checked first,
  then daily limit, then monthly limit, then balance. If a transfer would violate more than one simultaneously,
  the *first* one checked is the one reported — e.g. an amount that exceeds both the per-transfer and the daily
  limit always comes back as `PER_TRANSFER_LIMIT_EXCEEDED`. This ordering is fixed in `TransferService` and
  exercised by the existing limit-boundary tests.
- **Idempotency key**: caller-supplied (not a server-generated `Idempotency-Key` header convention), required,
  8–100 characters, alphanumeric plus `. _ : -`. It never expires (no TTL) — the DB's `UNIQUE` constraint on
  `idempotency_key` makes a key permanently non-reusable, by design, for the lifetime of the data. Reusing a key
  with the *same* request payload replays the original result; reusing it with a *different* payload (different
  source account / destination IBAN / amount / description) is a client bug and returns
  `409 TRANSFER_CONFLICT` rather than silently either replaying the old transfer or creating a second one.
  A failed first attempt (validation/limit/balance rejection) never gets an idempotency row written at all
  (rejections happen before the `Transfer` entity is created), so retrying the same key after a failure simply
  re-evaluates the request from scratch — it is not "cached."
- **No pagination on `GET /api/transfers`**: the base requirements ask for "list" and "list by date interval,"
  not a paged list (paging only appears under the optional frontend nice-to-haves); the endpoint returns the
  full matching result set, newest first. A very wide date range is therefore a client-side/UI concern (narrow
  the range), not something the backend caps or rejects.
- **Failed transfers are not currently reachable from this flow**: every validation/limit/balance failure is
  rejected *before* a `Transfer` row is created (4xx/422 response, nothing persisted), so no business path
  currently produces a persisted `FAILED` row; the status/column exist for future failure modes (e.g. an async
  settlement step) and are exercised by `markFailed()` at the entity level.
- **Optimistic locking, not pessimistic**: concurrent transfers touching the same account either succeed in
  sequence or one gets `409 TRANSFER_CONFLICT` for the client to retry; there is a small theoretical race
  between the daily/monthly limit check (a read) and the debit (a write) under high concurrency on the *same*
  account — acceptable for this exercise, would move to a `SELECT ... FOR UPDATE` or a ledger-based balance
  under stricter concurrency requirements. The 50-way concurrent scenario test proves the outcome that actually
  matters regardless: the balance is always exactly correct and never negative.
- **Timezone for limit/statistics boundaries**: `Europe/Istanbul` (configurable via
  `swiftpay.business.zone-id`), not UTC and not each customer's own timezone — a single bank-wide business day
  definition, which is how real banks define "today" for limit purposes.
- **Request body size cap**: a flat 1 MB limit (`MaxBodySizeFilter`) on every request, generous for this API's
  small JSON payloads, returning `413 PAYLOAD_TOO_LARGE` with the standard error body instead of letting the
  servlet container reset the connection.

## Known Limitations / Out of Scope

These checklist areas were deliberately **not** implemented or load-tested — they are infrastructure/deployment
concerns disproportionate to this case study's scope, not functional gaps in the application code:

- **SC-20 HTTPS/TLS/HSTS**: TLS termination is a reverse-proxy/load-balancer/deployment concern, not an
  application-code concern; not configured here.
- **SC-34 Dependency vulnerability scanning** (OWASP Dependency-Check, Snyk, etc.): no scanner is wired into the
  build; dependency versions are reasonably current (Spring Boot 3.3.5, jjwt 0.12.6) but not continuously
  scanned.
- **PR-01/PR-02 Load testing / formal p95 latency targets**: no load-testing harness (e.g. Gatling/k6) is
  included; the 50-concurrent-request scenario test demonstrates correctness under concurrency, not throughput
  or latency under production-scale load (10k+ customers/day).
- **Distributed rate limiting**: `LoginRateLimiter` is in-memory, correct for a single instance; a
  horizontally-scaled deployment would need a shared store (Redis) instead — already called out under Security.
- **SC-23 CSRF**: not applicable — authentication is a stateless `Authorization: Bearer` JWT, never a cookie, so
  there is no CSRF attack surface to protect against.
- **CC-08/CC-09 DB-unavailable / crash-mid-transfer recovery drills**: relied on Spring's `@Transactional`
  (atomic commit/rollback) and PostgreSQL's own durability guarantees rather than being separately drilled by
  killing the DB or the process mid-transfer; the transactional boundary around debit+credit+persist is the
  actual guarantee here.
- **AC-04 Closed/blocked/frozen accounts**: no such state exists in the data model (see Assumptions) — out of
  scope since account lifecycle management was never part of this case study.
