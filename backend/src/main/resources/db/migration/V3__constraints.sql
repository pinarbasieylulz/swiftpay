-- Defense-in-depth: these invariants are already enforced in the service layer,
-- but DB-level CHECK constraints guarantee they can never be violated even by a
-- future bug, a manual UPDATE, or a different application connecting to this DB.

ALTER TABLE accounts ADD CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0);

ALTER TABLE transfers ADD CONSTRAINT chk_transfers_amount_positive CHECK (amount > 0);

ALTER TABLE customers ADD CONSTRAINT chk_customers_limits_positive
    CHECK (per_transfer_limit > 0 AND daily_limit > 0 AND monthly_limit > 0);
ALTER TABLE customers ADD CONSTRAINT chk_customers_limits_ordered
    CHECK (daily_limit >= per_transfer_limit AND monthly_limit >= daily_limit);

-- idempotency_key is already globally UNIQUE (V1); that alone satisfies "unique
-- idempotency key per customer" since a key can only ever belong to one customer
-- row (see TransferService: a key reused by a different customer is rejected).
