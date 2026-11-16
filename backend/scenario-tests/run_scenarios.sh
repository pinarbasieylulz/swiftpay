#!/usr/bin/env bash
# End-to-end user-scenario tests against a running SwiftPay backend.
# Usage: ./run_scenarios.sh (assumes backend is up on localhost:8080 and
# the dev DB has been reset to the V2__seed.sql state before running).
set -uo pipefail

BASE="http://localhost:8080"
PASS=0
FAIL=0
FAILED_NAMES=()

uid() { python3 -c "import uuid;print(uuid.uuid4())"; }

# assert_status NAME METHOD PATH TOKEN BODY EXPECTED_STATUS [JQ_FILTER EXPECTED_VALUE]
req() {
  local method="$1" path="$2" token="$3" body="$4"
  if [ -n "$token" ]; then
    if [ -n "$body" ]; then
      curl -s -w '\n%{http_code}' -X "$method" "$BASE$path" \
        -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "$body"
    else
      curl -s -w '\n%{http_code}' -X "$method" "$BASE$path" -H "Authorization: Bearer $token"
    fi
  else
    if [ -n "$body" ]; then
      curl -s -w '\n%{http_code}' -X "$method" "$BASE$path" -H "Content-Type: application/json" -d "$body"
    else
      curl -s -w '\n%{http_code}' -X "$method" "$BASE$path"
    fi
  fi
}

check() {
  local name="$1" expected_status="$2" response="$3" extra_check="${4:-}"
  local status body
  status=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')
  if [ "$status" != "$expected_status" ]; then
    echo "FAIL: $name -- expected status $expected_status, got $status. body=$body"
    FAIL=$((FAIL+1)); FAILED_NAMES+=("$name")
    return 1
  fi
  if [ -n "$extra_check" ]; then
    if ! echo "$body" | eval "$extra_check" >/dev/null 2>&1; then
      echo "FAIL: $name -- status ok ($status) but extra check failed: $extra_check. body=$body"
      FAIL=$((FAIL+1)); FAILED_NAMES+=("$name")
      return 1
    fi
  fi
  echo "PASS: $name"
  PASS=$((PASS+1))
}

echo "=== Resetting dev DB to seed state ==="
PGPASSWORD=swiftpay psql -h localhost -U swiftpay -d swiftpay -v ON_ERROR_STOP=1 -c "
TRUNCATE transfers;
UPDATE accounts SET balance=200000.00, version=0 WHERE iban='TR110006200000000011110001';
UPDATE accounts SET balance=50000.00, version=0 WHERE iban='TR110006200000000022220002';
" >/dev/null

ALICE_ACC="aaaaaaaa-1111-1111-1111-111111111111"
BOB_ACC="aaaaaaaa-2222-2222-2222-222222222222"
ALICE_IBAN="TR110006200000000011110001"
BOB_IBAN="TR110006200000000022220002"

ALICE_TOKEN=$(req POST /api/auth/login "" '{"username":"alice","password":"Passw0rd!"}' | sed '$d' | jq -r .token)
BOB_TOKEN=$(req POST /api/auth/login "" '{"username":"bob","password":"Passw0rd!"}' | sed '$d' | jq -r .token)

echo "=== SUCCESS SCENARIOS ==="

check "login success returns JWT" 200 "$(req POST /api/auth/login "" '{"username":"alice","password":"Passw0rd!"}')" \
  "jq -e '.token | length > 10'"

check "list my accounts shows balances" 200 "$(req GET /api/accounts/me "$ALICE_TOKEN" "")" \
  "jq -e '.[0].balance != null'"

check "my limits reflect configured limits" 200 "$(req GET /api/accounts/me/limits "$ALICE_TOKEN" "")" \
  "jq -e '.perTransferLimit == 50000.0'"

TX1_KEY=$(uid)
TX1_RESP=$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":100.00,\"description\":\"rent\",\"idempotencyKey\":\"$TX1_KEY\"}")
check "valid transfer completes" 201 "$TX1_RESP" "jq -e '.status == \"COMPLETED\"'"
TX1_ID=$(echo "$TX1_RESP" | sed '$d' | jq -r .id)

ALICE_BAL_AFTER=$(req GET /api/accounts/me "$ALICE_TOKEN" "" | sed '$d' | jq -r '.[] | select(.iban=="'"$ALICE_IBAN"'") | .balance')
BOB_BAL_AFTER=$(req GET /api/accounts/me "$BOB_TOKEN" "" | sed '$d' | jq -r '.[] | select(.iban=="'"$BOB_IBAN"'") | .balance')
if [ "$ALICE_BAL_AFTER" = "199900.0" ] || [ "$ALICE_BAL_AFTER" = "199900.00" ]; then
  echo "PASS: source balance debited correctly ($ALICE_BAL_AFTER)"; PASS=$((PASS+1))
else
  echo "FAIL: source balance debited correctly -- expected 199900.00, got $ALICE_BAL_AFTER"; FAIL=$((FAIL+1)); FAILED_NAMES+=("source balance debited")
fi
if [ "$BOB_BAL_AFTER" = "50100.0" ] || [ "$BOB_BAL_AFTER" = "50100.00" ]; then
  echo "PASS: destination balance credited correctly ($BOB_BAL_AFTER)"; PASS=$((PASS+1))
else
  echo "FAIL: destination balance credited correctly -- expected 50100.00, got $BOB_BAL_AFTER"; FAIL=$((FAIL+1)); FAILED_NAMES+=("destination balance credited")
fi

check "get transfer by id returns correct data" 200 "$(req GET /api/transfers/$TX1_ID "$ALICE_TOKEN" "")" \
  "jq -e '.amount == 100.0'"

check "list transfers shows the new transfer" 200 "$(req GET /api/transfers "$ALICE_TOKEN" "")" \
  "jq -e '.[0].id != null'"

TODAY=$(date -u +%Y-%m-%dT00:00:00Z)
TOMORROW=$(date -u -d '+1 day' +%Y-%m-%dT00:00:00Z 2>/dev/null || date -u -v+1d +%Y-%m-%dT00:00:00Z)
check "date-range filter returns transfers in range" 200 "$(req GET "/api/transfers?startDate=$TODAY&endDate=$TOMORROW" "$ALICE_TOKEN" "")" \
  "jq -e 'length >= 1'"

check "duplicate idempotency key returns same transfer, not a new one" 201 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":100.00,\"description\":\"rent\",\"idempotencyKey\":\"$TX1_KEY\"}")" \
  "jq -e '.id == \"$TX1_ID\"'"

ALICE_BAL_AFTER_DUP=$(req GET /api/accounts/me "$ALICE_TOKEN" "" | sed '$d' | jq -r '.[] | select(.iban=="'"$ALICE_IBAN"'") | .balance')
if [ "$ALICE_BAL_AFTER_DUP" = "$ALICE_BAL_AFTER" ]; then
  echo "PASS: balance unchanged after idempotent replay"; PASS=$((PASS+1))
else
  echo "FAIL: balance unchanged after idempotent replay -- was $ALICE_BAL_AFTER now $ALICE_BAL_AFTER_DUP"; FAIL=$((FAIL+1)); FAILED_NAMES+=("idempotent balance unchanged")
fi

check "monthly statistics return 6 months, most recent first" 200 "$(req GET /api/statistics/monthly "$ALICE_TOKEN" "")" \
  "jq -e 'length == 6'"

echo "=== ERROR SCENARIOS ==="

check "wrong password -> 401 AUTH_ERROR" 401 "$(req POST /api/auth/login "" '{"username":"alice","password":"wrong"}')" \
  "jq -e '.errorCode == \"AUTH_ERROR\"'"

check "no token -> 401 AUTH_ERROR with full error body" 401 "$(req GET /api/accounts/me "" "")" \
  "jq -e '.errorCode == \"AUTH_ERROR\" and .message != null and .traceId != null'"

check "garbage token -> 401 AUTH_ERROR" 401 "$(req GET /api/accounts/me "garbage.token.value" "")" \
  "jq -e '.errorCode == \"AUTH_ERROR\"'"

check "negative amount -> 400 VALIDATION_ERROR" 400 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":-5.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

check "zero amount -> 400 VALIDATION_ERROR" 400 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":0.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

check "too-short IBAN -> 400 VALIDATION_ERROR" 400 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"TR1\",\"amount\":10.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

check "nonexistent source account -> error, not 500" 404 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$(uid)\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":10.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"RESOURCE_NOT_FOUND\"'"

check "unknown destination IBAN -> DESTINATION_NOT_FOUND" 422 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"TR999999999999999999999999\",\"amount\":10.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"DESTINATION_NOT_FOUND\"'"

check "self transfer -> SELF_TRANSFER_NOT_ALLOWED" 422 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$ALICE_IBAN\",\"amount\":10.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"SELF_TRANSFER_NOT_ALLOWED\"'"

check "amount over per-transfer limit -> 422 PER_TRANSFER_LIMIT_EXCEEDED" 422 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":50000.01,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"PER_TRANSFER_LIMIT_EXCEEDED\"'"

# Lower Bob's balance below an amount that's still within his per-transfer/daily/monthly
# limits, so the balance check (not the limit check) is the one that rejects it.
PGPASSWORD=swiftpay psql -h localhost -U swiftpay -d swiftpay -v ON_ERROR_STOP=1 -c \
  "UPDATE accounts SET balance=100.00, version=version+1 WHERE iban='$BOB_IBAN';" >/dev/null
check "insufficient balance -> 422 INSUFFICIENT_BALANCE" 422 \
  "$(req POST /api/transfers "$BOB_TOKEN" "{\"sourceAccountId\":\"$BOB_ACC\",\"destinationIban\":\"$ALICE_IBAN\",\"amount\":200.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"INSUFFICIENT_BALANCE\"'"
PGPASSWORD=swiftpay psql -h localhost -U swiftpay -d swiftpay -v ON_ERROR_STOP=1 -c \
  "UPDATE accounts SET balance=50000.00, version=version+1 WHERE iban='$BOB_IBAN';" >/dev/null

check "IDOR: using another customer's source account -> 403 ACCESS_DENIED" 403 \
  "$(req POST /api/transfers "$BOB_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":10.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"ACCESS_DENIED\"'"

check "IDOR: fetching another customer's transfer by id -> 403" 403 \
  "$(req GET /api/transfers/$TX1_ID "$BOB_TOKEN" "")" \
  "jq -e '.errorCode == \"ACCESS_DENIED\"'"

check "nonexistent transfer id -> 404 RESOURCE_NOT_FOUND" 404 \
  "$(req GET /api/transfers/$(uid) "$ALICE_TOKEN" "")" \
  "jq -e '.errorCode == \"RESOURCE_NOT_FOUND\"'"

echo "=== LIMIT BOUNDARY SCENARIOS ==="

echo "--- resetting DB again for clean boundary math ---"
PGPASSWORD=swiftpay psql -h localhost -U swiftpay -d swiftpay -v ON_ERROR_STOP=1 -c "
TRUNCATE transfers;
UPDATE accounts SET balance=200000.00, version=0 WHERE iban='$ALICE_IBAN';
UPDATE accounts SET balance=50000.00, version=0 WHERE iban='$BOB_IBAN';
" >/dev/null

check "exactly at per-transfer limit (50000.00) -> accepted" 201 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":50000.00,\"description\":\"boundary\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.status == \"COMPLETED\"'"

check "0.01 over per-transfer limit (50000.01) -> rejected" 422 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":50000.01,\"description\":\"boundary\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"PER_TRANSFER_LIMIT_EXCEEDED\"'"

echo "--- resetting DB, testing daily limit boundary (limit=100000.00) ---"
PGPASSWORD=swiftpay psql -h localhost -U swiftpay -d swiftpay -v ON_ERROR_STOP=1 -c "
TRUNCATE transfers;
UPDATE accounts SET balance=200000.00, version=0 WHERE iban='$ALICE_IBAN';
UPDATE accounts SET balance=50000.00, version=0 WHERE iban='$BOB_IBAN';
" >/dev/null

check "daily limit: two 50000.00 transfers reaching exactly 100000.00 total -> both accepted (1st)" 201 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":50000.00,\"description\":\"d1\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.status == \"COMPLETED\"'"

check "daily limit: 2nd 50000.00 transfer reaching exactly 100000.00 -> accepted" 201 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":50000.00,\"description\":\"d2\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.status == \"COMPLETED\"'"

check "daily limit: next transfer of 0.01 (would make 100000.01) -> DAILY_LIMIT_EXCEEDED" 422 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":0.01,\"description\":\"d3\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"DAILY_LIMIT_EXCEEDED\"'"

echo "--- resetting DB, testing monthly limit boundary (limit=500000.00) via directly-inserted completed transfers ---"
# Seed 450000.00 of "already completed this month" spread across 5 *different* days
# (90000.00 each, each under the 100000.00 daily limit) so the daily-limit check
# doesn't trip before we can exercise the monthly boundary in isolation.
PGPASSWORD=swiftpay psql -h localhost -U swiftpay -d swiftpay -v ON_ERROR_STOP=1 -c "
TRUNCATE transfers;
UPDATE accounts SET balance=600000.00, version=0 WHERE iban='$ALICE_IBAN';
UPDATE accounts SET balance=50000.00, version=0 WHERE iban='$BOB_IBAN';
INSERT INTO transfers (id, idempotency_key, customer_id, source_account_id, destination_iban, destination_account_id, amount, currency, description, status, created_at, completed_at)
SELECT gen_random_uuid(), 'seed-monthly-' || gs, '11111111-1111-1111-1111-111111111111', '$ALICE_ACC', '$BOB_IBAN', '$BOB_ACC', 90000.00, 'TRY', 'seed', 'COMPLETED', d, d
FROM (
  SELECT gs, (date_trunc('month', now()) + (gs || ' days')::interval) AS d
  FROM generate_series(0, 4) AS gs
) sub
WHERE date(d) <> date(now());
" >/dev/null
SEEDED_COUNT=$(PGPASSWORD=swiftpay psql -h localhost -U swiftpay -d swiftpay -t -c "SELECT count(*) FROM transfers WHERE idempotency_key LIKE 'seed-monthly-%';" | tr -d ' ')
if [ "$SEEDED_COUNT" != "5" ]; then
  echo "  (warning: only $SEEDED_COUNT/5 monthly-seed rows inserted -- 'today' likely collided with a seed day; monthly boundary checks below may be skipped-equivalent)"
fi

check "monthly limit: transfer completing exactly 500000.00 for the month -> accepted" 201 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":50000.00,\"description\":\"m1\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.status == \"COMPLETED\"'"

check "monthly limit: next 0.01 transfer (would exceed 500000.00) -> MONTHLY_LIMIT_EXCEEDED" 422 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":0.01,\"description\":\"m2\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"MONTHLY_LIMIT_EXCEEDED\"'"

echo "--- resetting DB for minimum-amount boundary ---"
PGPASSWORD=swiftpay psql -h localhost -U swiftpay -d swiftpay -v ON_ERROR_STOP=1 -c "
TRUNCATE transfers;
UPDATE accounts SET balance=200000.00, version=0 WHERE iban='$ALICE_IBAN';
UPDATE accounts SET balance=50000.00, version=0 WHERE iban='$BOB_IBAN';
" >/dev/null

check "minimum valid amount 0.01 -> accepted" 201 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":0.01,\"description\":\"min\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.status == \"COMPLETED\"'"

check "amount 0.00 -> rejected" 400 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":0.00,\"description\":\"min\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

check "amount -0.01 -> rejected" 400 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":-0.01,\"description\":\"min\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

echo "=== MALICIOUS / EXTREME INPUT SCENARIOS ==="

check "absurdly large amount -> rejected (PER_TRANSFER_LIMIT_EXCEEDED), not 500" 422 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":99999999999999999999999999.99,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"PER_TRANSFER_LIMIT_EXCEEDED\"'"

check "amount as non-numeric string 'NaN' -> 400, not 500" 400 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":\"NaN\",\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

check "amount missing entirely -> 400, not 500" 400 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

# Payload must be 15-34 chars to get past @Size and actually reach the IBAN lookup
# (shorter payloads are correctly rejected by validation before that -- also safe,
# just a different, earlier layer).
SQLI_IBAN="' OR '1'='1' --xxx"
check "SQL-injection-shaped IBAN -> treated as a literal, unknown IBAN, not 500" 422 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$SQLI_IBAN\",\"amount\":10.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"DESTINATION_NOT_FOUND\"'"

check "10000-char IBAN -> 400, not 500" 400 \
  "$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$(python3 -c "print('A'*10000)")\",\"amount\":10.00,\"description\":\"x\",\"idempotencyKey\":\"$(uid)\"}")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

XSS_KEY=$(uid)
XSS_RESP=$(req POST /api/transfers "$ALICE_TOKEN" "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":10.00,\"description\":\"<script>alert(1)</script>\",\"idempotencyKey\":\"$XSS_KEY\"}")
check "XSS payload in description is stored/returned as plain escaped JSON string" 201 "$XSS_RESP" \
  "jq -e '.description == \"<script>alert(1)</script>\"'"

check "invalid UUID path param -> 400, not 500" 400 \
  "$(req GET /api/transfers/not-a-uuid "$ALICE_TOKEN" "")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

check "invalid startDate format -> 400, not 500" 400 \
  "$(req GET "/api/transfers?startDate=not-a-date" "$ALICE_TOKEN" "")" \
  "jq -e '.errorCode == \"VALIDATION_ERROR\"'"

check "endDate before startDate -> no crash (empty list), not 500" 200 \
  "$(req GET "/api/transfers?startDate=2026-12-01T00:00:00Z&endDate=2026-01-01T00:00:00Z" "$ALICE_TOKEN" "")" \
  "jq -e 'length == 0'"

echo "=== CONCURRENCY SCENARIO ==="

echo "--- resetting DB for concurrency test ---"
PGPASSWORD=swiftpay psql -h localhost -U swiftpay -d swiftpay -v ON_ERROR_STOP=1 -c "
TRUNCATE transfers;
UPDATE accounts SET balance=200000.00, version=0 WHERE iban='$ALICE_IBAN';
UPDATE accounts SET balance=50000.00, version=0 WHERE iban='$BOB_IBAN';
" >/dev/null

echo "--- firing 50 concurrent transfers of 1000.00 each from Alice to Bob ---"
mkdir -p /tmp/swiftpay-concurrency
rm -f /tmp/swiftpay-concurrency/*.out
for i in $(seq 1 50); do
  (curl -s -o "/tmp/swiftpay-concurrency/$i.out" -w '%{http_code}' -X POST "$BASE/api/transfers" \
    -H "Authorization: Bearer $ALICE_TOKEN" -H "Content-Type: application/json" \
    -d "{\"sourceAccountId\":\"$ALICE_ACC\",\"destinationIban\":\"$BOB_IBAN\",\"amount\":1000.00,\"description\":\"concurrent\",\"idempotencyKey\":\"$(uid)\"}" \
    > "/tmp/swiftpay-concurrency/$i.status") &
done
wait

COMPLETED_COUNT=0
CONFLICT_COUNT=0
OTHER_COUNT=0
for i in $(seq 1 50); do
  st=$(cat "/tmp/swiftpay-concurrency/$i.status" 2>/dev/null || echo "000")
  if [ "$st" = "201" ]; then COMPLETED_COUNT=$((COMPLETED_COUNT+1));
  elif [ "$st" = "409" ]; then CONFLICT_COUNT=$((CONFLICT_COUNT+1));
  else OTHER_COUNT=$((OTHER_COUNT+1)); echo "  unexpected status $st for request $i: $(cat /tmp/swiftpay-concurrency/$i.out 2>/dev/null)"; fi
done
echo "  completed=$COMPLETED_COUNT conflict(409)=$CONFLICT_COUNT other=$OTHER_COUNT"

if [ "$OTHER_COUNT" -eq 0 ]; then
  echo "PASS: no unexpected status codes under concurrency (no 500s)"; PASS=$((PASS+1))
else
  echo "FAIL: no unexpected status codes under concurrency"; FAIL=$((FAIL+1)); FAILED_NAMES+=("concurrency: no unexpected statuses")
fi

FINAL_ALICE_BAL=$(req GET /api/accounts/me "$ALICE_TOKEN" "" | sed '$d' | jq -r '.[] | select(.iban=="'"$ALICE_IBAN"'") | .balance')
EXPECTED_BAL=$(python3 -c "print(round(200000.00 - $COMPLETED_COUNT*1000.00, 2))")
if [ "$(python3 -c "print(abs(float('$FINAL_ALICE_BAL') - float('$EXPECTED_BAL')) < 0.001)")" = "True" ]; then
  echo "PASS: final balance matches debited sum exactly (no double-spend) -- balance=$FINAL_ALICE_BAL expected=$EXPECTED_BAL"; PASS=$((PASS+1))
else
  echo "FAIL: final balance matches debited sum -- balance=$FINAL_ALICE_BAL expected=$EXPECTED_BAL"; FAIL=$((FAIL+1)); FAILED_NAMES+=("concurrency: balance consistency")
fi

if (( $(python3 -c "print(1 if float('$FINAL_ALICE_BAL') >= 0 else 0)") )); then
  echo "PASS: balance never went negative under concurrency"; PASS=$((PASS+1))
else
  echo "FAIL: balance never went negative under concurrency"; FAIL=$((FAIL+1)); FAILED_NAMES+=("concurrency: non-negative balance")
fi

echo
echo "================================================"
echo "RESULTS: $PASS passed, $FAIL failed (total $((PASS+FAIL)))"
if [ "$FAIL" -gt 0 ]; then
  echo "Failed scenarios:"
  for n in "${FAILED_NAMES[@]}"; do echo "  - $n"; done
  exit 1
fi
echo "ALL SCENARIOS PASSED"
exit 0
