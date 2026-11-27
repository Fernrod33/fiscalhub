-- Fase 5: simulação de parcelamento e transação pelo motor único (regras como dado em resources/parcelamento).
-- Simulações gravadas antes ficam como "motor antigo" (versao_motor = 1): só leitura, recalculáveis com um clique (a
-- nova versão aponta para a anterior). Nas novas, o resultado inteiro (todas as modalidades, cronogramas e memória) fica
-- em JSON, com a versão das regras e dos índices usados e os parâmetros da projeção da SELIC.
ALTER TABLE simulacao_parcelamento
    ALTER COLUMN orgao DROP NOT NULL,
    ALTER COLUMN modalidade DROP NOT NULL,
    ALTER COLUMN valor_original DROP NOT NULL,
    ALTER COLUMN data_vencimento_original DROP NOT NULL,
    ADD COLUMN versao_motor      INTEGER        NOT NULL DEFAULT 1,
    ADD COLUMN versao            INTEGER        NOT NULL DEFAULT 1,
    ADD COLUMN anterior_id       BIGINT         REFERENCES simulacao_parcelamento (id) ON DELETE SET NULL,
    ADD COLUMN criado_por        BIGINT         REFERENCES usuario (id),
    ADD COLUMN parametros        JSONB,
    ADD COLUMN resultado         JSONB,
    ADD COLUMN versao_regras     VARCHAR(30),
    ADD COLUMN versao_indices    VARCHAR(80),
    ADD COLUMN recomendacao      VARCHAR(500),  -- melhor modalidade elegível por órgão, como texto
    ADD COLUMN custo_recomendado NUMERIC(15, 2);

ALTER TABLE parcela_simulacao DROP CONSTRAINT IF EXISTS parcela_simulacao_simulacao_id_fkey,
    ADD CONSTRAINT parcela_simulacao_simulacao_id_fkey FOREIGN KEY (simulacao_id) REFERENCES simulacao_parcelamento (id) ON DELETE CASCADE;

-- Regras da capacidade cujo texto veio de fonte secundária (o oficial não pôde ser baixado).
ALTER TABLE regra_capacidade_pagamento ADD COLUMN fonte_secundaria BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE regra_capacidade_pagamento SET fonte_secundaria = TRUE WHERE fonte_url NOT LIKE 'https://www.sefanet.pr.gov.br/%';

-- Débitos das simulações antigas: reimportar um documento apaga e recria os débitos; o vínculo não pode travar isso.
-- As novas guardam a chave de cada débito em parametros (estável entre reimportações) e o débito inteiro no resultado.
ALTER TABLE simulacao_debito DROP CONSTRAINT IF EXISTS simulacao_debito_debito_id_fkey,
    ADD CONSTRAINT simulacao_debito_debito_id_fkey FOREIGN KEY (debito_id) REFERENCES debito (id) ON DELETE CASCADE;
