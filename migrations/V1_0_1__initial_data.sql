-- Inserir roles iniciais
INSERT INTO auth.role (id, name) VALUES (1,'ROLE_SUPER'), (2,'ROLE_ADMIN'), (3,'ROLE_USER');

-- Inserir usuário admin/admin
INSERT INTO auth.users (username, password, email, enabled, account_non_expired, credentials_non_expired, account_non_locked, full_name)
VALUES ('admin', '$2a$10$dWAxzod0cjRMglEczA58UOPtbGeZAM8TB6h7lJbg4PoLzILvjn1E2', 'admin@admin.com', TRUE, TRUE, TRUE, TRUE, 'Administrador');

-- Associar usuário admin à role ROLE_ADMIN
INSERT INTO auth.users_roles (user_id, role_id)
SELECT u.id, r.id FROM auth.users u, auth.role r WHERE u.username = 'admin' AND r.name = 'ROLE_SUPER';

-- Inserir níveis de prioridade
INSERT INTO Level (id, name) VALUES (1, 'Baixo'), (2, 'Normal'), (3, 'Alto'), (4, 'Urgente');

-- Inserir tipos de mensagem
INSERT INTO message_type (id, name) VALUES (1, 'Notícia'), (2, 'Notificação'), (3, 'Alerta');
