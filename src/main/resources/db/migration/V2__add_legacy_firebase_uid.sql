ALTER TABLE users
    ADD COLUMN legacy_firebase_uid varchar(128);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_legacy_firebase_uid
    ON users(legacy_firebase_uid)
    WHERE legacy_firebase_uid IS NOT NULL;