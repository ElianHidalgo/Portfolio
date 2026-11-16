-- The auth module owns its own schema (one schema per module, see V7).
-- Only BCrypt hashes are stored; the account itself comes from the ADMIN_* environment at startup.

create schema if not exists auth;

create table auth.admin_user (
    id            bigserial    primary key,
    username      varchar(50)  not null unique,
    password_hash varchar(100) not null,
    updated_at    timestamptz  not null default now()
);

-- Data-only rights for the runtime role; created in this migration, so granted explicitly.
grant usage on schema auth to portfolio_app;
grant select, insert, update, delete on auth.admin_user to portfolio_app;
grant usage, select on sequence auth.admin_user_id_seq to portfolio_app;

-- Tables and sequences created by future migrations get the same data-only rights.
alter default privileges in schema auth
    grant select, insert, update, delete on tables to portfolio_app;
alter default privileges in schema auth
    grant usage, select on sequences to portfolio_app;
