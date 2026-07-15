-- Fiscal Hub Database Schema
-- PostgreSQL Database Schema

-- Create database
-- CREATE DATABASE fiscalhub;

-- Table: usuario (User accounts)
CREATE TABLE IF NOT EXISTS usuario (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    senha VARCHAR(120) NOT NULL,
    cpf VARCHAR(14),
    telefone VARCHAR(20),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table: empresa (Companies)
CREATE TABLE IF NOT EXISTS empresa (
    id BIGSERIAL PRIMARY KEY,
    razao_social VARCHAR(150) NOT NULL,
    nome_fantasia VARCHAR(150),
    cnpj VARCHAR(18) NOT NULL UNIQUE,
    inscricao_estadual VARCHAR(20),
    inscricao_municipal VARCHAR(20),
    endereco VARCHAR(200),
    cidade VARCHAR(100),
    estado VARCHAR(2),
    cep VARCHAR(10),
    telefone VARCHAR(20),
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT NOT NULL,
    FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);

-- Table: declaracao (Tax declarations)
CREATE TABLE IF NOT EXISTS declaracao (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(50) NOT NULL,
    periodo_inicio DATE NOT NULL,
    periodo_fim DATE NOT NULL,
    valor_total DECIMAL(15,2) NOT NULL,
    valor_imposto DECIMAL(15,2),
    status VARCHAR(30) NOT NULL,
    data_vencimento DATE,
    data_pagamento DATE,
    observacoes VARCHAR(500),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT NOT NULL,
    empresa_id BIGINT NOT NULL,
    FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id) ON DELETE CASCADE
);

-- Table: simulacao_parcelamento (Installment simulations)
CREATE TABLE IF NOT EXISTS simulacao_parcelamento (
    id BIGSERIAL PRIMARY KEY,
    numero_parcelas INTEGER NOT NULL,
    valor_parcela DECIMAL(15,2) NOT NULL,
    taxa_juros DECIMAL(5,2) NOT NULL,
    valor_total DECIMAL(15,2) NOT NULL,
    valor_juros DECIMAL(15,2) NOT NULL,
    aprovada BOOLEAN NOT NULL DEFAULT FALSE,
    observacoes VARCHAR(500),
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    declaracao_id BIGINT NOT NULL,
    FOREIGN KEY (declaracao_id) REFERENCES declaracao(id) ON DELETE CASCADE
);

-- Indexes for better query performance
CREATE INDEX idx_usuario_email ON usuario(email);
CREATE INDEX idx_empresa_cnpj ON empresa(cnpj);
CREATE INDEX idx_empresa_usuario ON empresa(usuario_id);
CREATE INDEX idx_declaracao_usuario ON declaracao(usuario_id);
CREATE INDEX idx_declaracao_empresa ON declaracao(empresa_id);
CREATE INDEX idx_declaracao_status ON declaracao(status);
CREATE INDEX idx_simulacao_declaracao ON simulacao_parcelamento(declaracao_id);
CREATE INDEX idx_simulacao_aprovada ON simulacao_parcelamento(aprovada);

-- Sample data for testing (optional)
-- Note: Password should be hashed using BCrypt before insertion
-- INSERT INTO usuario (nome, email, senha, cpf, telefone, ativo) 
-- VALUES ('Admin User', 'admin@fiscalhub.com', '<use-bcrypt-hashed-password>', '12345678901', '1234567890', TRUE);
