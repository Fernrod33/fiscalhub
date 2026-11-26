-- Papéis do banco, depois de toda migração (Flyway, como dono do schema). Idempotente.
-- fiscalhub_app: o login da aplicação; lê e grava as tabelas, sem DDL, sem acesso ao histórico do Flyway e, na
--   auditoria, só SELECT e INSERT.
-- fiscalhub_expurgo: sem login; dono de auditoria_expurgar, a única que apaga auditoria (o trigger ainda confere o prazo).
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'fiscalhub_app') THEN CREATE ROLE fiscalhub_app; END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'fiscalhub_expurgo') THEN CREATE ROLE fiscalhub_expurgo; END IF;
END $$;
-- a senha vem de DB_APP_PASSWORD (placeholder do Flyway) e não pode ter aspas simples; a do .env sai de openssl rand
ALTER ROLE fiscalhub_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD '${app_senha}';
ALTER ROLE fiscalhub_expurgo WITH NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO fiscalhub_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO fiscalhub_app;
REVOKE ALL ON flyway_schema_history FROM fiscalhub_app;
REVOKE UPDATE, DELETE, TRUNCATE ON auditoria_evento FROM fiscalhub_app;

GRANT SELECT, DELETE ON auditoria_evento TO fiscalhub_expurgo;
ALTER FUNCTION auditoria_expurgar(TIMESTAMP) OWNER TO fiscalhub_expurgo;
REVOKE ALL ON FUNCTION auditoria_expurgar(TIMESTAMP) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auditoria_expurgar(TIMESTAMP) TO fiscalhub_app;
