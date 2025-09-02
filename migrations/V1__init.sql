-- Flyway migration: inicialização do banco com todas entidades do package model

-- SCHEMA
CREATE SCHEMA IF NOT EXISTS auth;

-- ROLES
CREATE TABLE IF NOT EXISTS auth.role (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- USERS
CREATE TABLE IF NOT EXISTS auth.users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(20) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    account_non_expired BOOLEAN DEFAULT TRUE,
    credentials_non_expired BOOLEAN DEFAULT TRUE,
    account_non_locked BOOLEAN DEFAULT TRUE,
    full_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- USERS_ROLES
CREATE TABLE IF NOT EXISTS auth.users_roles (
    user_id INTEGER NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES auth.role(id) ON DELETE CASCADE
);

-- LEVEL
CREATE TABLE IF NOT EXISTS Level (
    id INTEGER PRIMARY KEY,
    nome VARCHAR(50) NOT NULL
);

-- MESSAGE_TYPE
CREATE TABLE IF NOT EXISTS message_type (
    id INTEGER PRIMARY KEY,
    tipo VARCHAR(50) NOT NULL
);

-- MESSAGE
CREATE TABLE IF NOT EXISTS message (
    id UUID PRIMARY KEY,
    content TEXT,
    user_id INTEGER NOT NULL,
    level_id INTEGER,
    type_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES auth.users(id),
    FOREIGN KEY (level_id) REFERENCES Level(id),
    FOREIGN KEY (type_id) REFERENCES message_type(id)
);

-- Inserir roles iniciais
INSERT INTO auth.role (name) VALUES ('ROLE_ADMIN'), ('ROLE_DTI'), ('ROLE_USER');

-- Inserir usuário admin/admin (senha em texto plano para exemplo, ideal usar hash)
INSERT INTO auth.users (username, password, email, enabled, account_non_expired, credentials_non_expired, account_non_locked, full_name)
VALUES ('admin', 'admin', 'admin@admin.com', TRUE, TRUE, TRUE, TRUE, 'Administrador');

-- Associar usuário admin à role ROLE_ADMIN
INSERT INTO auth.users_roles (user_id, role_id)
SELECT u.id, r.id FROM auth.users u, auth.role r WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN';

-- Inserir níveis de prioridade
INSERT INTO Level (id, nome) VALUES (1, 'Baixo'), (2, 'Normal'), (3, 'Alto'), (4, 'Urgente');

-- Inserir tipos de mensagem
INSERT INTO message_type (id, tipo) VALUES (1, 'Notícia'), (2, 'Notificação'), (3, 'Alerta');
