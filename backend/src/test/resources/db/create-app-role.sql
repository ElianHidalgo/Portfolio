-- Test-only: in production this LOGIN role is created in the Neon console (password never in SQL).
create role portfolio_app login password 'test';
