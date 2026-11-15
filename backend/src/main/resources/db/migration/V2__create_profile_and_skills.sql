-- Single-row table: the portfolio has exactly one owner profile.
create table profile (
    id           smallint     primary key default 1 check (id = 1),
    full_name    varchar(100) not null,
    headline_es  varchar(160) not null,
    headline_en  varchar(160) not null,
    about_es     text         not null,
    about_en     text         not null,
    location     varchar(100),
    email        varchar(254),
    github_url   varchar(500),
    linkedin_url varchar(500),
    avatar_url   varchar(500),
    cv_url_es    varchar(500),
    cv_url_en    varchar(500),
    updated_at   timestamptz  not null default now()
);

create table skill (
    id            bigserial   primary key,
    name          varchar(60) not null unique,
    category      varchar(20) not null
                  check (category in ('BACKEND', 'FRONTEND', 'DATABASE', 'DEVOPS', 'TOOLS')),
    display_order int         not null default 0
);
