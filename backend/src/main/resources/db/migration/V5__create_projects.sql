create table project (
    id             bigserial    primary key,
    slug           varchar(80)  not null unique
                   check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    title_es       varchar(120) not null,
    title_en       varchar(120) not null,
    summary_es     varchar(300) not null,
    summary_en     varchar(300) not null,
    description_es text         not null,
    description_en text         not null,
    technologies   varchar(40)[] not null default '{}',
    repo_url       varchar(500),
    demo_url       varchar(500),
    image_url      varchar(500),
    featured       boolean      not null default false,
    display_order  int          not null default 0,
    published      boolean      not null default true,
    created_at     timestamptz  not null default now()
);
