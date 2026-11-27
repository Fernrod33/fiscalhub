-- Linha calculada pelo sistema, sem conta equivalente no plano oficial: hoje, o "Resultado do período não encerrado"
-- no PL do balanço montado de balancete em planilha antes do encerramento. A origem lista as contas somadas.
ALTER TABLE linha_demonstracao ADD COLUMN derivada BOOLEAN NOT NULL DEFAULT FALSE;
