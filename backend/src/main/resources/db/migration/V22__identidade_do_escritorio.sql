-- Identidade do escritório nos relatórios entregues ao cliente: nome de exibição, logotipo (chave no storage; PNG
-- já redimensionado no servidor), cor principal (#rrggbb, contraste AA verificado), rodapé e responsável técnico padrão.
ALTER TABLE escritorio
    ADD COLUMN nome_exibicao         VARCHAR(150),
    ADD COLUMN logo_chave            VARCHAR(300),
    ADD COLUMN cor_principal         VARCHAR(7),
    ADD COLUMN rodape_relatorio      VARCHAR(500),
    ADD COLUMN responsavel_nome      VARCHAR(150),
    ADD COLUMN responsavel_registro  VARCHAR(60);
