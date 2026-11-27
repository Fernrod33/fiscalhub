-- O convite por e-mail substitui a senha provisória. Quem ainda estava com ela vira convite pendente e perde a senha
-- ('!' não é um hash BCrypt: nenhuma senha confere) até o ADMIN reenviar o convite; sessões abertas caem.
UPDATE sessao SET revogada_em = now()
WHERE revogada_em IS NULL AND usuario_id IN (SELECT id FROM usuario WHERE trocar_senha_no_proximo_login);

UPDATE usuario SET convite_pendente = true, senha = '!', versao_token = versao_token + 1
WHERE trocar_senha_no_proximo_login;

ALTER TABLE usuario DROP COLUMN trocar_senha_no_proximo_login;
