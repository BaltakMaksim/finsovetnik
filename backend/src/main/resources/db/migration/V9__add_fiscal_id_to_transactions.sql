-- Добавляем поле для уникального идентификатора чека
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS fiscal_id VARCHAR(255);

-- Создаём уникальный индекс (только для непустых значений)
CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_fiscal_id 
ON transactions(fiscal_id) 
WHERE fiscal_id IS NOT NULL;

