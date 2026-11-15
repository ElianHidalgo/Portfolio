-- Sample content so the site never starts empty. Edit it from the admin panel.
insert into profile (full_name, headline_es, headline_en, about_es, about_en, github_url)
values (
    'Elian Hidalgo',
    'Desarrollador full-stack — Java, Spring y Angular',
    'Full-stack developer — Java, Spring & Angular',
    E'Construyo aplicaciones web de punta a punta con Java, Spring Boot y Angular.\n\nEste texto es de ejemplo — edítalo desde el panel de administración.',
    E'I build web applications end to end with Java, Spring Boot and Angular.\n\nThis text is sample content — edit it from the admin panel.',
    'https://github.com/ElianHidalgo'
);

insert into skill (name, category, display_order) values
    ('Java',        'BACKEND',  1),
    ('Spring Boot', 'BACKEND',  2),
    ('Angular',     'FRONTEND', 3),
    ('TypeScript',  'FRONTEND', 4),
    ('PostgreSQL',  'DATABASE', 5),
    ('Docker',      'DEVOPS',   6),
    ('Git',         'TOOLS',    7);
