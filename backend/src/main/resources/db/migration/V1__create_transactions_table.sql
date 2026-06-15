-- Создание таблицы транзакций
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    amount DOUBLE PRECISION NOT NULL,
    category VARCHAR(50) NOT NULL,
    owner VARCHAR(50) NOT NULL,
    reply TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для быстрого поиска по владельцу
CREATE INDEX idx_transactions_owner ON transactions(owner);

-- Индекс для быстрого поиска по дате
CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);