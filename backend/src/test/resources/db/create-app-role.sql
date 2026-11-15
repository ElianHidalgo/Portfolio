-- Test-only. In production create this role with SQL as the owner (never in the Neon console:
-- console roles join neon_superuser and get CREATEROLE/CREATEDB and read/write on everything).
create role portfolio_app login password 'test';
