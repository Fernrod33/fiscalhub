-- Retenção da auditoria. UPDATE e DELETE continuam bloqueados pelo trigger; a única exceção é o DELETE feito dentro de
-- auditoria_expurgar(limite), que marca o limite numa variável da transação, e só para eventos anteriores a ele.
-- ponytail: a aplicação e a função usam o mesmo papel do banco, então isto barra o DELETE comum (acidente, bug), não
-- um papel comprometido que imite a marca; separação real = função SECURITY DEFINER de outro dono + REVOKE do DELETE.

CREATE OR REPLACE FUNCTION auditoria_somente_insercao() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE'
        AND OLD.data_hora < NULLIF(current_setting('fiscalhub.auditoria_expurgo_ate', true), '')::timestamp THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'auditoria_evento aceita somente insercao';
END;
$$ LANGUAGE plpgsql;

-- Remove os eventos com data_hora < limite e devolve quantos. Chamada só pelo job de retenção (RetencaoAuditoria).
CREATE FUNCTION auditoria_expurgar(limite TIMESTAMP) RETURNS INTEGER AS $$
DECLARE
    removidos INTEGER;
BEGIN
    PERFORM set_config('fiscalhub.auditoria_expurgo_ate', limite::text, true);
    DELETE FROM auditoria_evento WHERE data_hora < limite;
    GET DIAGNOSTICS removidos = ROW_COUNT;
    PERFORM set_config('fiscalhub.auditoria_expurgo_ate', '', true);
    RETURN removidos;
END;
$$ LANGUAGE plpgsql;
