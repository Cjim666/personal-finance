CREATE TABLE import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    duplicate_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at DATETIME NOT NULL,
    confirmed_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_batch_file_hash (file_hash)
);

CREATE TABLE finance_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_batch_id BIGINT NOT NULL,
    source VARCHAR(32) NOT NULL,
    external_id VARCHAR(128),
    business_fingerprint CHAR(64) NOT NULL,
    occurred_at DATETIME NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    account_name VARCHAR(255),
    counterparty VARCHAR(255),
    summary VARCHAR(1000),
    category VARCHAR(100),
    raw_data JSON,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transaction_source_external (source, external_id),
    UNIQUE KEY uk_transaction_fingerprint (business_fingerprint),
    KEY idx_transaction_occurred_at (occurred_at),
    KEY idx_transaction_direction_occurred_at (direction, occurred_at),
    CONSTRAINT fk_transaction_import_batch FOREIGN KEY (import_batch_id) REFERENCES import_batch (id)
);
