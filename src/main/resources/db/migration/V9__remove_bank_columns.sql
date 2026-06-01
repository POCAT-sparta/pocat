-- V9: Remove bank account columns from users table (#168)
-- bank_name and bank_account are no longer used in the application

ALTER TABLE users DROP COLUMN bank_name;
ALTER TABLE users DROP COLUMN bank_account;
