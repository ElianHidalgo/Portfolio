-- Replaces the sample seed with the owner's real profile (source: content/profile.md).
-- From part 8 on, this content is edited from the admin panel.

update profile set
    full_name    = 'Victor Hidalgo',
    headline_es  = 'Estudiante de Ingeniería de Software enfocado en backend y DevOps',
    headline_en  = 'Software Engineering student focused on backend and DevOps',
    about_es     = E'Estudio Ingeniería de Software en la Universidad de las Américas (UDLA), en Quito, y busco una pasantía en desarrollo backend o DevOps.\n\n'
                   'En mi pasantía actual en la UDLA automatizo procesos con RPA —uno de los bots redujo un 75 % el tiempo de procesamiento manual—, doy soporte N2 sobre el ERP Banner y escribo consultas en SQL Server y PL/SQL.\n\n'
                   'Fuera del trabajo construyo proyectos propios con Java, Spring Boot y .NET. Este portafolio es uno de ellos: una API en Spring Boot y un frontend en Angular, con el código abierto en GitHub.',
    about_en     = E'I''m a Software Engineering student at Universidad de las Américas (UDLA) in Quito, looking for an internship in backend development or DevOps.\n\n'
                   'In my current internship at UDLA I automate processes with RPA — one of the bots cut manual processing time by 75% — provide Level 2 support on the Banner ERP, and write SQL Server and PL/SQL queries.\n\n'
                   'Outside work I build my own projects with Java, Spring Boot and .NET. This portfolio is one of them: a Spring Boot API and an Angular front end, with the source code open on GitHub.',
    location     = 'Quito, Ecuador',
    email        = 'victor.hidalgo.escalante@outlook.com',
    github_url   = 'https://github.com/ElianHidalgo',
    linkedin_url = 'https://www.linkedin.com/in/victor-hidalgo-889249303/',
    cv_url_es    = 'cv/cv-es.pdf',
    cv_url_en    = 'cv/cv-en.pdf',
    updated_at   = now();

delete from skill;

insert into skill (name, category, display_order) values
    ('Java',                      'BACKEND',   1),
    ('Spring Boot',               'BACKEND',   2),
    ('C#',                        'BACKEND',   3),
    ('.NET',                      'BACKEND',   4),
    ('Python',                    'BACKEND',   5),
    ('Angular',                   'FRONTEND',  6),
    ('TypeScript',                'FRONTEND',  7),
    ('.NET MAUI',                 'FRONTEND',  8),
    ('SQL Server',                'DATABASE',  9),
    ('PL/SQL',                    'DATABASE', 10),
    ('PostgreSQL',                'DATABASE', 11),
    ('SQLite',                    'DATABASE', 12),
    ('Docker',                    'DEVOPS',   13),
    ('Git',                       'DEVOPS',   14),
    ('GitHub',                    'DEVOPS',   15),
    ('Linux',                     'DEVOPS',   16),
    ('Bash',                      'DEVOPS',   17),
    ('Automation Anywhere (RPA)', 'TOOLS',    18),
    ('Banner Ellucian',           'TOOLS',    19),
    ('Active Directory',          'TOOLS',    20);
