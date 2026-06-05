-- V10: Remove bank account columns from users table (#168)
-- bank_name and bank_account are no longer used in the application

SET @drop_bank_name = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'bank_name') > 0,
        'ALTER TABLE users DROP COLUMN bank_name',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_bank_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_bank_account = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'bank_account') > 0,
        'ALTER TABLE users DROP COLUMN bank_account',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_bank_account;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
