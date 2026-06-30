ALTER TABLE transactions ADD COLUMN IF NOT EXISTS receipt_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_transactions_receipt_id ON transactions(receipt_id);