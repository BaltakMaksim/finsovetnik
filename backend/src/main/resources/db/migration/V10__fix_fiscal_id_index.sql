-- Удаляем UNIQUE индекс
DROP INDEX IF EXISTS idx_transactions_fiscal_id;

-- Создаём обычный индекс (без UNIQUE) для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_transactions_fiscal_id 
ON transactions(fiscal_id) 
WHERE fiscal_id IS NOT NULL;