ALTER TABLE finance_transaction
    ALTER COLUMN business_fingerprint CHAR(64) NOT NULL;

ALTER TABLE import_batch
    ALTER COLUMN file_hash CHAR(64) NOT NULL;
