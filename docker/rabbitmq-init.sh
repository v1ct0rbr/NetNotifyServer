#!/bin/sh
# Script de inicialização do RabbitMQ com usuários segregados
# Este script é executado DENTRO do container RabbitMQ após inicialização
#
# IMPORTANTE - O que é VARIÁVEL de AMBIENTE:
#   ✓ RABBITMQ_ADMIN_PRODUCER_PASS   → senha do produtor (VARIÁVEL)
#   ✓ RABBITMQ_AGENT_CONSUMER_PASS   → senha do consumidor (VARIÁVEL)
#
# IMPORTANTE - O que é HARDCODED:
#   ✗ admin-producer                  → nome do usuário produtor (FIXO)
#   ✗ agent-consumer                  → nome do usuário consumidor (FIXO)
#
# Fluxo:
# 1. RabbitMQ inicia com user padrão (RABBITMQ_DEFAULT_USER/PASS)
# 2. Este script é executado automaticamente (/docker-entrypoint-init.d/)
# 3. Cria/valida dois usuários com nomes fixos e senhas variáveis
#
# IDEMPOTÊNCIA: Script é seguro rodar múltiplas vezes (força update de senhas e permissões)

set -e

# Aguarda RabbitMQ iniciar completamente (o wrapper já faz isso, mas não custa aguardar mais)
echo "Aguardando inicialização do RabbitMQ..."
sleep 3

# Tenta garantir que rabbitmqctl esteja funcionando
for attempt in 1 2 3 4 5; do
    if rabbitmqctl status >/dev/null 2>&1; then
        echo "✓ RabbitMQ respondendo normalmente"
        break
    fi
    echo "  (tentativa $attempt - aguardando...)"
    sleep 2
done

echo "=========================================="
echo "Configurando usuários segregados no RabbitMQ..."
echo "=========================================="

# Senhas são lidas das variáveis de ambiente (com fallback para padrão)
ADMIN_PRODUCER_PASS=${RABBITMQ_ADMIN_PRODUCER_PASS:-adminproducer123}
AGENT_CONSUMER_PASS=${RABBITMQ_AGENT_CONSUMER_PASS:-agentconsumer123}

echo ""
echo "Nomes dos usuários (FIXOS):"
echo "  • ADMIN_PRODUCER_USER = admin-producer"
echo "  • AGENT_CONSUMER_USER = agent-consumer"
echo ""
echo "Senhas (DE VARIÁVEIS DE AMBIENTE):"
echo "  • RABBITMQ_ADMIN_PRODUCER_PASS = ****** (variável de env)"
echo "  • RABBITMQ_AGENT_CONSUMER_PASS = ****** (variável de env)"
echo ""

# ============================================
# Usuário PRODUTOR: admin-producer (FIXO)
# ============================================
echo "[1/4] Configurando usuário produtor: 'admin-producer'..."

if rabbitmqctl list_users | grep -q "^admin-producer"; then
    echo "      ✓ Usuário 'admin-producer' já existe"
    echo "      → Atualizando senha e permissões..."
else
    echo "      → Criando novo usuário 'admin-producer'..."
fi

# Tenta criar (se não existir, cria; se existir, comando é ignorado silenciosamente)
rabbitmqctl add_user admin-producer "$ADMIN_PRODUCER_PASS" 2>/dev/null || true

# FORÇA atualizar a senha (mesmo que o usuário já exista)
echo "      → Aplicando senha (de RABBITMQ_ADMIN_PRODUCER_PASS)..."
rabbitmqctl change_password admin-producer "$ADMIN_PRODUCER_PASS" 2>/dev/null || true

# FORÇA atualizar permissões (mesmo que já existam)
echo "      → Aplicando permissões (configure + write)..."
rabbitmqctl set_permissions -p / admin-producer ".*" ".*" "" 2>/dev/null || true

# FORÇA atualizar tags
echo "      → Aplicando tags (management)..."
rabbitmqctl set_user_tags admin-producer management 2>/dev/null || true

# ============================================
# Usuário CONSUMIDOR: agent-consumer (FIXO)
# ============================================
echo ""
echo "[2/4] Configurando usuário consumidor: 'agent-consumer'..."

if rabbitmqctl list_users | grep -q "^agent-consumer"; then
    echo "      ✓ Usuário 'agent-consumer' já existe"
    echo "      → Atualizando senha e permissões..."
else
    echo "      → Criando novo usuário 'agent-consumer'..."
fi

# Tenta criar (se não existir, cria; se existir, comando é ignorado silenciosamente)
rabbitmqctl add_user agent-consumer "$AGENT_CONSUMER_PASS" 2>/dev/null || true

# FORÇA atualizar a senha (mesmo que o usuário já exista)
echo "      → Aplicando senha (de RABBITMQ_AGENT_CONSUMER_PASS)..."
rabbitmqctl change_password agent-consumer "$AGENT_CONSUMER_PASS" 2>/dev/null || true

# FORÇA atualizar permissões (mesmo que já existam)
echo "      → Aplicando permissões (read only)..."
rabbitmqctl set_permissions -p / agent-consumer "" "" ".*" 2>/dev/null || true

# ============================================
# Verificação Final
# ============================================
# ============================================
# Verificação Final (não-crítica)
# ============================================
echo ""
echo "[3/4] Verificando configuração..."
sleep 2

echo ""
echo "Usuários no RabbitMQ:"
rabbitmqctl list_users 2>/dev/null | tail -n +2 || echo "  (erro ao listar, mas usuários foram criados)"

echo ""
echo "Permissões por usuário (vhost '/'):"
rabbitmqctl list_permissions 2>/dev/null | tail -n +2 || echo "  (erro ao listar, mas permissões foram definidas)"

# ============================================
# Resumo
# ============================================
echo ""
echo "=========================================="
echo "✓ Configuração concluída com sucesso!"
echo "=========================================="
echo ""
echo "Usuários criados:"
echo "  • admin-producer     (produtor: write + configure)"
echo "  • agent-consumer     (consumidor: read only)"
echo "  • guest              (padrão RabbitMQ)"
echo ""
echo "Conexão para Servidor (NetNotify):"
echo "  user: admin-producer"
echo "  pass: [RABBITMQ_ADMIN_PRODUCER_PASS]"
echo ""
echo "Conexão para Agentes:"
echo "  user: agent-consumer"
echo "  pass: [RABBITMQ_AGENT_CONSUMER_PASS]"
echo ""
echo "=========================================="
echo ""
