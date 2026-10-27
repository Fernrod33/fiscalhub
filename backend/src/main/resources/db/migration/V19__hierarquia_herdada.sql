-- Código referencial do I051 ausente na tabela oficial do exercício: entra na demonstração referencial na posição da
-- tabela do ano anterior mais recente em que existe (alerta CODIGO_REFERENCIAL_AUSENTE_NO_ANO). Guarda esse ano.
ALTER TABLE linha_demonstracao ADD COLUMN hierarquia_herdada_de INTEGER;
