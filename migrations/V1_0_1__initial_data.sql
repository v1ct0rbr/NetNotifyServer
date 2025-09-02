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
