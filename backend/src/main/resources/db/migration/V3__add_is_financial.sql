ALTER TABLE transactions 
ADD COLUMN is_financial BOOLEAN NOT NULL DEFAULT true;