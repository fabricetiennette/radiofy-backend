create table if not exists refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,

    token_hash varchar(255) not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,

    revoked_at timestamptz null,
    ip varchar(64) null,
    user_agent varchar(255) null
    );

create index if not exists idx_refresh_tokens_user_id on refresh_tokens(user_id);
create index if not exists idx_refresh_tokens_expires_at on refresh_tokens(expires_at);