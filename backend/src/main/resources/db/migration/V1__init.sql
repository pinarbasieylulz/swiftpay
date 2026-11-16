CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    per_transfer_limit    NUMERIC(19,2) NOT NULL DEFAULT 50000.00,
    daily_limit           NUMERIC(19,2) NOT NULL DEFAULT 100000.00,
    monthly_limit         NUMERIC(19,2) NOT NULL DEFAULT 500000.00,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    customer_id     UUID NOT NULL REFERENCES customers(id),
    iban            VARCHAR(34) NOT NULL UNIQUE,
    balance         NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3) NOT NULL DEFAULT 'TRY',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_customer ON accounts(customer_id);

CREATE TABLE transfers (
    id                  UUID PRIMARY KEY,
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,
    customer_id         UUID NOT NULL REFERENCES customers(id),
    source_account_id   UUID NOT NULL REFERENCES accounts(id),
    destination_iban    VARCHAR(34) NOT NULL,
    destination_account_id UUID REFERENCES accounts(id),
    amount              NUMERIC(19,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'TRY',
    description         VARCHAR(255),
    status              VARCHAR(20) NOT NULL,
    failure_reason      VARCHAR(255),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    completed_at        TIMESTAMP
);
CREATE INDEX idx_transfers_customer ON transfers(customer_id);
CREATE INDEX idx_transfers_customer_created ON transfers(customer_id, created_at);
CREATE INDEX idx_transfers_source_account_status_created ON transfers(source_account_id, status, created_at);
