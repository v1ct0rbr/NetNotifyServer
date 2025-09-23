-- Flyway migration

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
    name VARCHAR(50) NOT NULL
);

-- MESSAGE_TYPE
CREATE TABLE IF NOT EXISTS message_type (
    id INTEGER PRIMARY KEY,
    name VARCHAR(50) NOT NULL
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
