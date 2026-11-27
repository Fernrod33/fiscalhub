-- LGPD: exclusão do cliente com prazo de arrependimento, aceite dos termos por usuário e o encarregado (DPO) do escritório.

-- Exclusão pedida (com a dupla confirmação) e quando ela será concluída pelo job; nulo = cliente normal.
ALTER TABLE cliente
    ADD COLUMN exclusao_solicitada_em  TIMESTAMP(6),
    ADD COLUMN exclusao_solicitada_por BIGINT REFERENCES usuario (id),
    ADD COLUMN exclusao_prevista_para  TIMESTAMP(6);

-- Versão dos Termos de Uso e da Política de Privacidade aceita no primeiro acesso (e de novo quando a versão mudar).
ALTER TABLE usuario
    ADD COLUMN termos_versao     VARCHAR(30),
    ADD COLUMN termos_aceitos_em TIMESTAMP(6);

-- Contato do encarregado pelo tratamento de dados pessoais do escritório (controlador), LGPD art. 41.
ALTER TABLE escritorio
    ADD COLUMN encarregado_nome  VARCHAR(150),
    ADD COLUMN encarregado_email VARCHAR(150);
