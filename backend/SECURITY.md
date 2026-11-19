# Security — Implementation Details

Full detail behind the "Security" summary in [README.md](README.md). This is a banking domain, so every item
below was verified live against a running instance, not just unit-tested.

- **Authentication**: stateless JWT (`io.jsonwebtoken`, HS256), issued by `POST /api/auth/login`, required on
  every other endpoint. A missing/invalid/expired token returns `401 AUTH_ERROR` with the standard error body
  (not an empty response — this was a real bug found and fixed during review: the Spring Security entry point
  used to call `response.sendError()` directly, bypassing `GlobalExceptionHandler`).
- **Password storage**: BCrypt (`BCryptPasswordEncoder`, default cost factor 10).
- **Timing-attack resistant login**: whether the username exists or not, `AuthService.login` always performs a
  BCrypt comparison (against a fixed dummy hash when the user is unknown) before rejecting, so response time
  cannot be used to enumerate valid usernames.
- **Brute-force protection**: `LoginRateLimiter` enforces two independent in-memory, 60-second fixed windows:
  5 failed attempts per IP+username (stops guessing one account) and 20 failed attempts per IP across *any*
  usernames (stops credential-stuffing many accounts from one IP, which the first limit alone would not catch).
  Both return `429 TOO_MANY_ATTEMPTS`. The client IP is read from `HttpServletRequest.getRemoteAddr()` only —
  `X-Forwarded-For` is deliberately **not** trusted, because this service is not deployed behind a reverse proxy
  in this case study and a client-supplied header could otherwise be forged per-request to bypass the limiter
  entirely (this was found and fixed during a security review; an earlier version did trust it). If this is ever
  deployed behind a real reverse proxy, that proxy's address must be allow-listed before `X-Forwarded-For` is
  honored again. In-memory and per-instance — sufficient for a single-node deployment; a multi-instance
  deployment would need a shared store (e.g. Redis) instead.
- **Transport security (HTTPS/TLS)**: the app serves HTTPS by default on port `8443` using a local self-signed
  certificate (`scripts/generate-keystore.sh` generates `src/main/resources/keystore.p12`, gitignored — run it
  once before `mvn spring-boot:run`; `curl -k` or an equivalent "trust this cert" step is needed against the
  self-signed cert). A second plaintext connector on port `8080` (`TlsConfig`) does nothing but 302-redirect to
  HTTPS — there is no unencrypted path to the API when TLS is enabled (the default). `Strict-Transport-Security`
  is sent on every HTTPS response. Set `SSL_ENABLED=false` to disable TLS entirely for local HTTP-only debugging.
  In a real deployment, the self-signed dev certificate would be replaced by one from a real CA (directly or via
  a TLS-terminating load balancer/reverse proxy) — the app-level config is what changes, not the approach.
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
- **Security headers**: verified live with `curl -i` against the running HTTPS instance. Present on every
  response (including error responses): `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Strict-Transport-Security`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`,
  `Referrer-Policy: no-referrer`, `Permissions-Policy: geolocation=(), camera=(), microphone=(), payment=()`,
  and cache-control on sensitive responses. No `Server` header is sent (Tomcat does not add one by default), so
  the server software/version is not disclosed.
- **JWT hardening, verified live**: a hand-crafted `alg: none` token is rejected with `401` (jjwt refuses to
  parse an unsigned token as if it were signed); a ~50 KB oversized `Authorization` header returns `400`, not a
  crash; a tampered payload with the original signature fails signature verification and returns `401`.
- **Actuator exposure**: only `/actuator/health` is reachable without a token (`200`); every other actuator
  path (`/actuator`, `/actuator/env`, etc.) requires authentication like any other endpoint (`401` without a
  token) — verified live. `management.endpoints.web.exposure.include` is intentionally left at its Spring Boot
  default (health only), so no new actuator endpoint is accidentally exposed by a future dependency bump.
- **No sensitive fields in responses**: verified live that `GET /api/accounts/me` and every other response never
  include the password hash or any other internal-only field — DTOs only ever expose what the API contract
  defines.
- **Dependency currency**: direct dependencies are pinned to current stable releases (Spring Boot 3.3.5, jjwt
  0.12.6). A full OWASP Dependency-Check scan was attempted (`org.owasp:dependency-check-maven`) but could not
  complete in this environment: its vulnerability feed downloads (e.g. the CISA KEV feed) are blocked by the
  sandbox's network egress policy (`403 Forbidden`), not by anything in this project. Running
  `mvn org.owasp:dependency-check-maven:check` in an environment with open internet access is recommended before
  a real deployment.

Known, accepted limitation: the rate limiter and the idempotency/limit-check-then-write sequence are correct
for a single application instance; a horizontally-scaled deployment would need a shared rate-limit store and
(for the small theoretical read-then-write race noted in [ASSUMPTIONS.md](ASSUMPTIONS.md)) a stricter locking
strategy. Stateless JWT also has no server-side "logout": a token already issued stays valid until it expires
(kept short, 1 hour, for this reason) because revoking it would require a server-side blacklist (e.g. Redis)
that this case study's scope does not include — this is a deliberate, documented trade-off, not an oversight.

## Dependency vulnerability scanning / load testing / other out-of-scope items

See the "Known Limitations / Out of Scope" section in [ASSUMPTIONS.md](ASSUMPTIONS.md).
