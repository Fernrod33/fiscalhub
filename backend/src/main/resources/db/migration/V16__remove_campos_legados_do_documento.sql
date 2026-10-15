-- Campos do cadastro manual de documentos (antes da importação por conteúdo): esfera, jurisdição, tributos e
-- retificação não são mais informados nem lidos. Tributo, esfera e retificação passam a vir do próprio arquivo
-- (apuracao_tributo e situação da escrituração). Os tipos com nome de tributo não são mais detectados: viram OUTRO.
DROP TABLE documento_fiscal_tributos;
ALTER TABLE documento_fiscal
    DROP COLUMN esfera,
    DROP COLUMN juridicao,
    DROP COLUMN retificacao;

UPDATE documento_fiscal SET tipo = 'DCTFWEB' WHERE tipo = 'DCTF_WEB';
UPDATE documento_fiscal SET tipo_informado = 'DCTFWEB' WHERE tipo_informado = 'DCTF_WEB';
UPDATE documento_fiscal SET tipo = 'OUTRO'
WHERE tipo IN ('IRPJ', 'CSLL', 'PIS', 'COFINS', 'IPI', 'ICMS', 'DEMONSTRACAO_CONTABIL');
UPDATE documento_fiscal SET tipo_informado = NULL
WHERE tipo_informado IN ('IRPJ', 'CSLL', 'PIS', 'COFINS', 'IPI', 'ICMS', 'DEMONSTRACAO_CONTABIL');
