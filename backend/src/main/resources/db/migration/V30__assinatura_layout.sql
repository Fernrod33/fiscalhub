-- Assinatura do layout de um documento que nenhum adaptador reconhece (a 1ª linha de texto, sem dígitos): base das
-- métricas do ADMIN do sistema sobre quais documentos ainda não têm leitura.
ALTER TABLE documento_fiscal ADD COLUMN assinatura_layout VARCHAR(200);
