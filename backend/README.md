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
- **Security**: stateless JWT auth, BCrypt passwords, IDOR protection on every lookup — see [SECURITY.md](SECURITY.md) for the full picture.
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

A ready-to-import Postman collection is at `postman/SwiftPay.postman_collection.json` (`baseUrl` points at
`https://localhost:8443`; since the dev certificate is self-signed, turn off "SSL certificate verification" in
Postman's settings, or run with `SSL_ENABLED=false` for plain HTTP instead).

## Running it

PostgreSQL 16 must be running locally with two databases: `swiftpay` (app) and `swiftpay_test` (integration
tests), owned by role `swiftpay`/`swiftpay`. In this container that was already set up with:

```bash
service postgresql start
sudo -u postgres psql -c "CREATE USER swiftpay WITH PASSWORD 'swiftpay' CREATEDB;"
sudo -u postgres psql -c "CREATE DATABASE swiftpay OWNER swiftpay;"
sudo -u postgres psql -c "CREATE DATABASE swiftpay_test OWNER swiftpay;"
```

Generate the local dev TLS keystore once (gitignored, not committed — see [SECURITY.md](SECURITY.md)):

```bash
./scripts/generate-keystore.sh
```

(Windows without Git Bash/WSL: the script needs `bash` and `keytool` on PATH. Either run it from Git Bash/WSL,
or run the equivalent command directly with a JDK's `keytool` on PATH:
`keytool -genkeypair -alias swiftpay -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 -keystore src/main/resources/keystore.p12 -storepass changeit -keypass changeit -dname "CN=localhost, OU=SwiftPay, O=SwiftPay, L=Istanbul, ST=Istanbul, C=TR"`.
Or skip this step entirely and set `SSL_ENABLED=false` below — see the note under "Running it".)

Start the app (Flyway migrates the schema and seeds demo data automatically):

```bash
mvn spring-boot:run
```

API listens on **`https://localhost:8443`** (self-signed dev certificate — use `curl -k` or equivalent).
Plain `http://localhost:8080` is kept open only to 302-redirect to HTTPS. Set `SSL_ENABLED=false` to serve
plain HTTP on `8443` instead, e.g. for quick local debugging without the keystore. **If you do this, also
change `VITE_API_BASE_URL` in `frontend/.env` to `http://localhost:8443/api`** — the frontend and backend
scheme must match, or requests fail (see `frontend/README.md`).

Run the test suite (unit + integration, against `swiftpay_test`):

```bash
mvn test
```

JaCoCo report: `target/site/jacoco/index.html`. **Backend test coverage: ~97% instruction coverage** (92
unit/integration tests; see `target/site/jacoco/jacoco.csv` for the authoritative, regenerated-on-every-run
number — well above the 80% requirement). The only meaningfully uncovered lines are `SwiftpayApplication.main`,
the HTTPS/Tomcat-connector wiring in `TlsConfig` (infrastructure glue, exercised live instead — see Security),
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

## Security (summary)

Stateless JWT auth + BCrypt, timing-attack-resistant login, brute-force rate limiting, HTTPS/TLS by default,
IDOR protection on every lookup, locked-down CORS, no information leakage in errors, no SQL/log injection
surface, security headers verified live, JWT hardening verified live, actuator locked down, no sensitive
fields ever returned. **Full detail, each item verified live against a running instance → [SECURITY.md](SECURITY.md).**

## Assumptions & Known Limitations (summary)

Single currency/system (TRY only, internal IBANs only), no registration/KYC (seeded data), limits are
per-customer, a transfer is only visible to its sender (not the receiver), deterministic limit-violation
priority, idempotency key semantics, no pagination on the list endpoint (not required — see frontend for
client-side paging), optimistic locking trade-offs, `Europe/Istanbul` business timezone. Also: what's
genuinely out of scope (load testing, dependency scanning blocked by sandbox network policy, distributed
rate limiting) vs. what's done. **Full detail + the audit trail of every gap found and fixed during an
exhaustive ~200-item test-checklist review → [ASSUMPTIONS.md](ASSUMPTIONS.md).**
