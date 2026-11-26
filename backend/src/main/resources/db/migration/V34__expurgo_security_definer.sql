-- O expurgo da auditoria roda com os direitos do dono da função (SECURITY DEFINER), não de quem chama. O dono é o
-- papel fiscalhub_expurgo (sem login, só SELECT e DELETE em auditoria_evento); a aplicação (fiscalhub_app) só tem
-- EXECUTE nela e nenhum DELETE na tabela (substitui o ponytail da V31). Papéis, dono e permissões ficam em
-- afterMigrate.sql, que roda a cada subida: papéis não vão no pg_dump, e um backup restaurado num servidor novo
-- precisa deles de volta.
-- search_path fixo: numa função SECURITY DEFINER, um objeto homônimo em outro schema não pode tomar o lugar da tabela.
ALTER FUNCTION auditoria_expurgar(TIMESTAMP) SECURITY DEFINER SET search_path = public, pg_temp;
