-- Demo/test data. Password for all demo users is "Passw0rd!" (see README).
INSERT INTO customers (id, username, password_hash, full_name, per_transfer_limit, daily_limit, monthly_limit)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'alice', '$2a$10$aDiX1gq88gog14ZUjg1WAeXr22vswVgUh52IwxRTQPQYo7xoJUo2O', 'Alice Yilmaz', 50000.00, 100000.00, 500000.00),
    ('22222222-2222-2222-2222-222222222222', 'bob', '$2a$10$aDiX1gq88gog14ZUjg1WAeXr22vswVgUh52IwxRTQPQYo7xoJUo2O', 'Bob Demir', 50000.00, 100000.00, 500000.00);

INSERT INTO accounts (id, customer_id, iban, balance, currency)
VALUES
    ('aaaaaaaa-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'TR110006200000000011110001', 200000.00, 'TRY'),
    ('aaaaaaaa-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'TR110006200000000022220002', 50000.00, 'TRY');
