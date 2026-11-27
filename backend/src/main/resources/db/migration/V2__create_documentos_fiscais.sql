-- IF NOT EXISTS: bancos de dev antigos ja tem estas tabelas (criadas pelo ddl-auto=update).
CREATE TABLE IF NOT EXISTS documento_fiscal (
    id BIGSERIAL PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    esfera VARCHAR(20) NOT NULL,
    juridicao VARCHAR(20) NOT NULL,
    periodo_inicio DATE NOT NULL,
    periodo_fim DATE NOT NULL,
    nome_original_arquivo VARCHAR(255) NOT NULL,
    nome_arquivo_interno VARCHAR(255) NOT NULL,
    extensao VARCHAR(20) NOT NULL,
    mime_type VARCHAR(150) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    data_importacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) NOT NULL,
    retificacao BOOLEAN NOT NULL DEFAULT FALSE,
    documento_original_id BIGINT,
    observacao VARCHAR(1000),
    caminho_arquivo VARCHAR(500) NOT NULL,
    data_atualizacao TIMESTAMP,
    CONSTRAINT fk_documento_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_documento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_documento_original FOREIGN KEY (documento_original_id) REFERENCES documento_fiscal(id)
);

CREATE TABLE IF NOT EXISTS documento_fiscal_tributos (
    documento_id BIGINT NOT NULL,
    tributo VARCHAR(30) NOT NULL,
    PRIMARY KEY (documento_id, tributo),
    CONSTRAINT fk_documento_tributo FOREIGN KEY (documento_id) REFERENCES documento_fiscal(id)
);
