-- ip_address est nullable dans ton entité -> pas besoin de backfill
alter table refresh_tokens
    add column if not exists ip_address inet;

-- optionnel mais probable que ça te tombe dessus juste après :
alter table refresh_tokens
    add column if not exists user_agent text;

-- optionnel aussi (colonne nullable dans ton entité)
alter table refresh_tokens
    add column if not exists parent_id uuid;