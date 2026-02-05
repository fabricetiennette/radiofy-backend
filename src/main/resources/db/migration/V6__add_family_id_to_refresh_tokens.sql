-- 1) Permet d'utiliser gen_random_uuid()
create extension if not exists pgcrypto;

-- 2) Ajout de la colonne (nullable au début)
alter table refresh_tokens
    add column if not exists family_id uuid;

-- 3) Backfill des lignes existantes (au cas où)
update refresh_tokens
set family_id = gen_random_uuid()
where family_id is null;

-- 4) Maintenant on peut imposer NOT NULL (comme ton entité)
alter table refresh_tokens
    alter column family_id set not null;

-- (optionnel) index utile si tu filtres par family_id
create index if not exists idx_refresh_tokens_family_id
    on refresh_tokens(family_id);