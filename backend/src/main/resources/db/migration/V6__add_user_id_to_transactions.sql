ALTER TABLE transactions 
ADD COLUMN user_id BIGINT REFERENCES users(id);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);
