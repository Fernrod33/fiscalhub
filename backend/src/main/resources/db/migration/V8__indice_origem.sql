-- Origem e data da coleta de cada indice (ex.: BCB_SGS_4390 para a SELIC importada do Banco Central).
ALTER TABLE indice_monetario
    ADD COLUMN origem      VARCHAR(30),
    ADD COLUMN data_coleta TIMESTAMP(6);

UPDATE indice_monetario SET origem = 'LEGADO' WHERE origem IS NULL;
