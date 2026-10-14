-- versao_token: incrementada na troca de senha, desativacao e mudanca de papel; tokens com versao antiga deixam de valer.
ALTER TABLE usuario ADD COLUMN versao_token INTEGER NOT NULL DEFAULT 0;

-- Limpeza do audit: retificacao volta na Fase 1; o nome interno ja esta no fim da chave_armazenamento.
ALTER TABLE documento_fiscal DROP COLUMN documento_original_id;
ALTER TABLE documento_fiscal DROP COLUMN nome_arquivo_interno;
