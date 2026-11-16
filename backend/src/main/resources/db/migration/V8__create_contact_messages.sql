-- The contact module owns its own schema (one schema per module, see V7).
-- portfolio_app already exists: created by V7, or by SQL beforehand in production.

create schema if not exists contact;

create table contact.message (
    id         bigserial    primary key,
    name       varchar(100)  not null,
    email      varchar(254)  not null,
    body       varchar(2000) not null,
    locale     varchar(2)    not null check (locale in ('es', 'en')),
    created_at timestamptz   not null default now(),
    read       boolean       not null default false
);

-- The daily cap counts messages of the last 24 hours; the admin lists newest first.
create index message_created_at_idx on contact.message (created_at);

-- Data-only rights for the runtime role; created in this migration, so granted explicitly.
grant usage on schema contact to portfolio_app;
grant select, insert, update, delete on contact.message to portfolio_app;
grant usage, select on sequence contact.message_id_seq to portfolio_app;

-- Tables and sequences created by future migrations get the same data-only rights.
alter default privileges in schema contact
    grant select, insert, update, delete on tables to portfolio_app;
alter default privileges in schema contact
    grant usage, select on sequences to portfolio_app;
