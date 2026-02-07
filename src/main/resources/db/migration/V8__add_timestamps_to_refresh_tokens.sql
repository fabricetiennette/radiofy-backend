-- 1) issued_at (NOT NULL côté entité)
alter table refresh_tokens
    add column if not exists issued_at timestamptz;

update refresh_tokens
set issued_at = coalesce(issued_at, created_at, now())
where issued_at is null;

alter table refresh_tokens
    alter column issued_at set not null;

-- 2) expires_at (NOT NULL côté entité)
alter table refresh_tokens
    add column if not exists expires_at timestamptz;

-- si tu as déjà des rows, il faut une valeur; adapte selon ton métier
update refresh_tokens
set expires_at = coalesce(expires_at, issued_at + interval '30 days')
where expires_at is null;

alter table refresh_tokens
    alter column expires_at set not null;

-- 3) created_at (NOT NULL côté entité)
alter table refresh_tokens
    add column if not exists created_at timestamptz;

update refresh_tokens
set created_at = coalesce(created_at, issued_at, now())
where created_at is null;

alter table refresh_tokens
    alter column created_at set not null;

-- 4) colonnes nullable dans l’entité (tu peux les mettre ici aussi)
alter table refresh_tokens add column if not exists used_at timestamptz;
alter table refresh_tokens add column if not exists revoked_at timestamptz;