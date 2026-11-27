-- Bancos que passaram pelo ddl-auto=update (antes do Flyway) têm um UNIQUE (cnpj) global com nome gerado
-- pelo Hibernate (ex.: uk74xhe5obsc7li6x4q5wi75pd5), que a V4 não removeu (ela só tirou o empresa_cnpj_key).
-- Ele impede o mesmo cliente em dois escritórios e o 409 revela o CNPJ de outro escritório.
-- Vale o UNIQUE (escritorio_id, cnpj) da V4.
DO $$
DECLARE
    restricao text;
BEGIN
    FOR restricao IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'cliente'::regclass AND contype = 'u' AND pg_get_constraintdef(oid) = 'UNIQUE (cnpj)'
    LOOP
        EXECUTE format('ALTER TABLE cliente DROP CONSTRAINT %I', restricao);
    END LOOP;
END $$;
