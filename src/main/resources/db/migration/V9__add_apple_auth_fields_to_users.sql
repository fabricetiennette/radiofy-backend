ALTER TABLE users
    ADD COLUMN apple_subject VARCHAR(255);

ALTER TABLE users
    ADD COLUMN auth_provider VARCHAR(20);

UPDATE users
SET auth_provider = 'LOCAL'
WHERE auth_provider IS NULL;

ALTER TABLE users
    ALTER COLUMN auth_provider SET NOT NULL;

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_apple_subject UNIQUE (apple_subject);