-- The publishing module owns its own schema (one schema per module, see V7).
-- One row per accepted "Publicar": when it was requested and Cloudflare's build id, if given.

create schema if not exists publishing;

create table publishing.publication (
    id           bigserial   primary key,
    requested_at timestamptz not null default now(),
    build_id     varchar(64)
);

create index publication_requested_at_idx on publishing.publication (requested_at);

-- Data-only rights for the runtime role; created in this migration, so granted explicitly.
grant usage on schema publishing to portfolio_app;
grant select, insert, update, delete on publishing.publication to portfolio_app;
grant usage, select on sequence publishing.publication_id_seq to portfolio_app;

-- Tables and sequences created by future migrations get the same data-only rights.
alter default privileges in schema publishing
    grant select, insert, update, delete on tables to portfolio_app;
alter default privileges in schema publishing
    grant usage, select on sequences to portfolio_app;
