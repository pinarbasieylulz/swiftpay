# Assumptions, Known Limitations & Audit Trail

Full detail behind the "Assumptions" summary in [README.md](README.md). Every subjective/design decision
surfaced during development — including an exhaustive ~200-item test-checklist audit — is written down here,
with the behavior it produces and how it's verified, not just noted.

## Assumptions

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
  not a paged list (paging only appears under the optional frontend nice-to-haves, and is implemented
  client-side there — see `frontend/README.md`); the endpoint returns the full matching result set, newest
  first. A very wide date range is therefore a client-side/UI concern (narrow the range), not something the
  backend caps or rejects.
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

The case study document itself only names one thing out of scope: *"User registration, KYC, cards, loans and
any integration with external payment networks."* Nothing below is excused by that — each item here is instead
either **done** (and linked to where), or a **named technical trade-off with a reason**, never a vague "out of
scope":

- **HTTPS/TLS/HSTS — done**, not skipped: see [SECURITY.md](SECURITY.md). The one thing genuinely left for a
  real deployment is swapping the local self-signed certificate for one from a real CA (or a TLS-terminating
  load balancer in front) — the app-level mechanism is already in place and tested live.
- **SC-34 Dependency vulnerability scanning**: attempted (`org.owasp:dependency-check-maven`), not skipped —
  see [SECURITY.md](SECURITY.md) for why it could not complete in this sandboxed environment (its vulnerability
  feed is blocked by network egress policy, not by anything in this project) and what to run once that's lifted.
- **PR-01/PR-02 Load testing / formal p95 latency targets**: genuinely not attempted — a real load-testing
  harness (Gatling/k6) sized to simulate "10k+ customers/day" needs infrastructure (a load generator, a
  realistically-provisioned target, time) this environment/exercise doesn't have. What *was* proven instead: the
  50-concurrent-request scenario test demonstrates correctness (no double-spend, no overdraft, no 500s) under
  real concurrency — a different, narrower claim than throughput/latency under production-scale load.
- **Distributed rate limiting**: `LoginRateLimiter` is in-memory, correct for a single instance; a
  horizontally-scaled deployment would need a shared store (Redis) instead — already called out in
  [SECURITY.md](SECURITY.md).
- **Stateless JWT has no server-side logout**: a deliberate trade-off, not a gap — see the note at the end of
  [SECURITY.md](SECURITY.md).
- **SC-23 CSRF**: not applicable — authentication is a stateless `Authorization: Bearer` JWT, never a cookie, so
  there is no CSRF attack surface to protect against.
- **CC-08/CC-09 DB-unavailable / crash-mid-transfer recovery drills**: relied on Spring's `@Transactional`
  (atomic commit/rollback) and PostgreSQL's own durability guarantees rather than being separately drilled by
  killing the DB or the process mid-transfer; the transactional boundary around debit+credit+persist is the
  actual guarantee here.
- **AC-04 Closed/blocked/frozen accounts**: no such state exists in the data model (see Assumptions above) —
  out of scope since account lifecycle management was never part of this case study.

## Audit trail: extra test coverage added during an exhaustive scenario-checklist pass

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
