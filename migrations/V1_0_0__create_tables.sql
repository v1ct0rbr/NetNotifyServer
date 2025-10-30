-- Flyway migration

-- SCHEMA
CREATE SCHEMA IF NOT EXISTS auth;

-- ROLES

-- USERS
CREATE TABLE IF NOT EXISTS auth.users (
    id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    first_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN DEFAULT TRUE,
    preferences TEXT,
    theme VARCHAR(50),
    language VARCHAR(10),
    timezone VARCHAR(50)    
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
    title VARCHAR(100),
    content TEXT,
    user_id VARCHAR(50),
    level_id INTEGER,
    type_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES auth.users(id),
    FOREIGN KEY (level_id) REFERENCES Level(id),
    FOREIGN KEY (type_id) REFERENCES message_type(id)
);
