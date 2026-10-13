-- Empresa vira Cliente e passa a pertencer ao escritorio. O dono anterior fica registrado como criado_por.

ALTER TABLE empresa RENAME TO cliente;
ALTER TABLE cliente RENAME COLUMN usuario_id TO criado_por_id;
ALTER TABLE cliente ADD COLUMN escritorio_id BIGINT;

UPDATE cliente c
SET escritorio_id = u.escritorio_id
FROM usuario u
WHERE u.id = c.criado_por_id;

ALTER TABLE cliente
    ALTER COLUMN escritorio_id SET NOT NULL,
    ADD CONSTRAINT fk_cliente_escritorio FOREIGN KEY (escritorio_id) REFERENCES escritorio (id);

-- CNPJ unico por escritorio (antes era global: impedia dois escritorios com o mesmo cliente
-- e o 409 revelava a existencia do CNPJ em outro escritorio)
ALTER TABLE cliente DROP CONSTRAINT IF EXISTS empresa_cnpj_key;
ALTER TABLE cliente ADD CONSTRAINT uk_cliente_escritorio_cnpj UNIQUE (escritorio_id, cnpj);

ALTER TABLE declaracao RENAME COLUMN empresa_id TO cliente_id;
ALTER TABLE documento_fiscal RENAME COLUMN empresa_id TO cliente_id;
CREATE INDEX ix_documento_fiscal_cliente ON documento_fiscal (cliente_id);
