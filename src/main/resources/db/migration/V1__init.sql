create extension if not exists pgcrypto;

create table if not exists users (
                                     id uuid primary key default gen_random_uuid(),
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    display_name varchar(255),
    photo_url text,
    role varchar(30) not null default 'USER',
    created_at timestamptz not null default now()
    );

create table if not exists favorites (
                                         id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    station_id varchar(64) not null,
    created_at timestamptz not null default now(),
    unique (user_id, station_id)
    );

create index if not exists idx_favorites_user on favorites(user_id);