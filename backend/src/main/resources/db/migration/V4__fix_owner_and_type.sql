-- Делаем owner nullable (AI не всегда возвращает владельца)
ALTER TABLE transactions 
ALTER COLUMN owner DROP NOT NULL;

-- Исправляем опечатку EXPONSE → EXPENSE
UPDATE transactions 
SET type = 'EXPENSE' 
WHERE type = 'EXPONSE';

-- Меняем дефолтное значение на правильное
ALTER TABLE transactions 
ALTER COLUMN type SET DEFAULT 'EXPENSE';