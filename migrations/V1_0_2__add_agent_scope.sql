-- Flyway migration: adiciona campo agent_scope para controle de visibilidade por tipo de agente

ALTER TABLE message ADD COLUMN IF NOT EXISTS agent_scope VARCHAR(20) NOT NULL DEFAULT 'BOTH';
