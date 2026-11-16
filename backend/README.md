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

JaCoCo report: `target/site/jacoco/index.html` (current instruction coverage ≈ 91%).

## Demo credentials (seeded by `V2__seed.sql`)

| Username | Password | IBAN | Starting balance |
|---|---|---|---|
| `alice` | `Passw0rd!` | `TR110006200000000011110001` | 200,000.00 TRY |
| `bob` | `Passw0rd!` | `TR110006200000000022220002` | 50,000.00 TRY |

## Configuration

Limits are configurable in `application.yml` under `swiftpay.limits` (used only to seed customer defaults;
each customer's live limits are columns on `customers`, so they can also be changed per customer without a
redeploy). JWT secret/expiry under `swiftpay.security`.

## Assumptions

- **Single currency, single system**: all accounts are TRY and transfers only move money between two accounts
  that already exist in SwiftPay's own database (resolved by IBAN) — no external bank network, no FX.
- **IBAN format is simplified**: validated only for length (15–34 chars), not with the real IBAN checksum/
  country-format algorithm, since IBAN issuance is out of scope (no registration flow exists).
- **No customer registration/KYC endpoint**: customers and accounts are seeded directly via Flyway, since
  registration/KYC was explicitly out of scope.
- **Limits are per-customer, not per-account**: a customer with multiple accounts shares one daily/monthly cap
  across all their accounts (debits only; incoming transfers don't count against the sender's limit obviously,
  and don't count against the receiver's limit either, since a limit caps how much a customer can *send*).
- **Idempotency key is caller-supplied** (not a server-generated request header convention like
  `Idempotency-Key`) and is a required field on `TransferRequest`, scoped per logical transfer-attempt by the
  client.
- **Failed transfers are not currently reachable from this flow**: every validation/limit/balance failure is
  rejected *before* a `Transfer` row is created (4xx/422 response, nothing persisted), so no business path
  currently produces a persisted `FAILED` row; the status/column exist for future failure modes (e.g. an async
  settlement step) and are exercised by `markFailed()` at the entity level.
- **Optimistic locking, not pessimistic**: concurrent transfers touching the same account either succeed in
  sequence or one gets `409 TRANSFER_CONFLICT` for the client to retry; there is a small theoretical race
  between the daily/monthly limit check (a read) and the debit (a write) under high concurrency on the *same*
  account — acceptable for this exercise, would move to a `SELECT ... FOR UPDATE` or a ledger-based balance
  under stricter concurrency requirements.
- Dates/limits use UTC calendar days/months (not the customer's local timezone).
