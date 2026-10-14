-- Bancos que passaram pelo ddl-auto=update (antes do Flyway) ganharam do Hibernate um CHECK por coluna enum
-- (ex.: documento_fiscal_tipo_check = ANY (ARRAY['IRPJ', ...])). Eles recusam todo valor novo (BALANCETE,
-- AGUARDANDO_CLIENTE...) e contrariam a regra das migrações: enums são VARCHAR sem CHECK (V1). As migrações nunca
-- criaram CHECK assim; os que existirem são resto do ddl-auto e saem.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT conrelid::regclass AS tabela, conname
        FROM pg_constraint
        WHERE contype = 'c' AND connamespace = 'public'::regnamespace
          AND conname LIKE '%\_check' AND pg_get_constraintdef(oid) LIKE '%= ANY (%ARRAY[%' -- "= ANY ((ARRAY['A', ...]"
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tabela, r.conname);
    END LOOP;
END $$;
