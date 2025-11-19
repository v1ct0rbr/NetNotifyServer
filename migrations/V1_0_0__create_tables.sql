-- Flyway migration

-- SCHEMA
CREATE SCHEMA IF NOT EXISTS auth;

-- USERS
CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY,
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
    timezone VARCHAR(50),
    roles TEXT
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

-- DEPARTMENT
CREATE TABLE IF NOT EXISTS department (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    parent_department_id UUID REFERENCES department(id) ON DELETE SET NULL
);

-- MESSAGE
CREATE TABLE IF NOT EXISTS message (
    id UUID PRIMARY KEY,
    title VARCHAR(100),
    content TEXT,
    user_id UUID NOT NULL,
    level_id INTEGER,
    type_id INTEGER,
    repeat_interval_minutes INTEGER,
    expire_at TIMESTAMP,
    last_sent_at TIMESTAMP,
    published_at TIMESTAMP,
    send_to_subdivisions BOOLEAN,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
    FOREIGN KEY (level_id) REFERENCES Level(id) ON DELETE SET NULL,
    FOREIGN KEY (type_id) REFERENCES message_type(id) ON DELETE SET NULL
);

-- MESSAGE_DEPARTMENT (Join table for many-to-many relationship)
CREATE TABLE IF NOT EXISTS message_department (
    message_id UUID NOT NULL,
    department_id UUID NOT NULL,
    PRIMARY KEY (message_id, department_id),
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE,
    FOREIGN KEY (department_id) REFERENCES department(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_user_email ON auth.users(email);
CREATE INDEX IF NOT EXISTS idx_user_username ON auth.users(username);
CREATE INDEX IF NOT EXISTS idx_message_user_id ON message(user_id);
CREATE INDEX IF NOT EXISTS idx_message_level_id ON message(level_id);
CREATE INDEX IF NOT EXISTS idx_message_type_id ON message(type_id);
CREATE INDEX IF NOT EXISTS idx_department_parent ON department(parent_department_id);
CREATE INDEX IF NOT EXISTS idx_message_department_dept ON message_department(department_id);

