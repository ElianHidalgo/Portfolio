-- The owner's UDLA internship ended on 2026-11-12 (May–November 2026): the "about" text now
-- describes it in the past tense, and the internship search adds infrastructure (source:
-- content/profile.md).

update profile.profile set
    about_es   = E'Estudio Ingeniería de Software en la Universidad de las Américas (UDLA), en Quito, y busco una pasantía en desarrollo backend, DevOps o infraestructura.\n\n'
                 'Entre mayo y noviembre de 2026 hice mi pasantía en la UDLA: automaticé procesos con RPA —uno de los bots redujo un 75 % el tiempo de procesamiento manual—, di soporte N2 sobre el ERP Banner y escribí consultas en SQL Server y PL/SQL.\n\n'
                 'Fuera del trabajo construyo proyectos propios con Java, Spring Boot y .NET. Este portafolio es uno de ellos: una API en Spring Boot y un frontend en Angular, con el código abierto en GitHub.',
    about_en   = E'I''m a Software Engineering student at Universidad de las Américas (UDLA) in Quito, looking for an internship in backend development, DevOps or infrastructure.\n\n'
                 'From May to November 2026 I interned at UDLA, where I automated processes with RPA — one of the bots cut manual processing time by 75% — provided Level 2 support on the Banner ERP, and wrote SQL Server and PL/SQL queries.\n\n'
                 'Outside work I build my own projects with Java, Spring Boot and .NET. This portfolio is one of them: a Spring Boot API and an Angular front end, with the source code open on GitHub.',
    updated_at = now();
