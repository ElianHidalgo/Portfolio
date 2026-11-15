-- Least privilege for a modular monolith:
-- 1. Each module owns a schema, so data ownership is visible in the database too.
-- 2. The application connects as portfolio_app, which may read and write data but never
--    create, alter or drop tables. Migrations run as the owner role (DB_MIGRATION_*).

create schema if not exists profile;
create schema if not exists projects;

-- Owned sequences (bigserial) move with their tables.
alter table public.profile set schema profile;
alter table public.skill set schema profile;
alter table public.project set schema projects;

-- In production the LOGIN role is created in the Neon console beforehand (its password
-- never appears in SQL). Elsewhere a NOLOGIN placeholder keeps the grants valid.
do $$
begin
    if not exists (select from pg_roles where rolname = 'portfolio_app') then
        create role portfolio_app nologin;
    end if;
end
$$;

grant usage on schema profile, projects to portfolio_app;
grant select, insert, update, delete on all tables in schema profile, projects to portfolio_app;
grant usage, select on all sequences in schema profile, projects to portfolio_app;

-- Tables and sequences created by future migrations get the same data-only rights.
alter default privileges in schema profile, projects
    grant select, insert, update, delete on tables to portfolio_app;
alter default privileges in schema profile, projects
    grant usage, select on sequences to portfolio_app;

-- Nobody but the owner may create objects in the shared public schema.
revoke create on schema public from public;
