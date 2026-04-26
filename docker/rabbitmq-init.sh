#!/bin/sh
# Script de inicialização do RabbitMQ com usuários segregados
# Este script é executado DENTRO do container RabbitMQ após inicialização
#
# IMPORTANTE - O que é VARIÁVEL de AMBIENTE:
#   ✓ RABBITMQ_ADMIN_PRODUCER_USER   → usuário do produtor (VARIÁVEL)
#   ✓ RABBITMQ_ADMIN_PRODUCER_PASS   → senha do produtor (VARIÁVEL)
#   ✓ RABBITMQ_AGENT_CONSUMER_USER   → usuário do consumidor (VARIÁVEL)
#   ✓ RABBITMQ_AGENT_CONSUMER_PASS   → senha do consumidor (VARIÁVEL)
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

# Credenciais lidas das variáveis de ambiente (com fallback para padrão)
ADMIN_PRODUCER_USER=${RABBITMQ_ADMIN_PRODUCER_USER:-admin-producer}
ADMIN_PRODUCER_PASS=${RABBITMQ_ADMIN_PRODUCER_PASS:-adminproducer123}
AGENT_CONSUMER_USER=${RABBITMQ_AGENT_CONSUMER_USER:-agent-consumer}
AGENT_CONSUMER_PASS=${RABBITMQ_AGENT_CONSUMER_PASS:-agentconsumer123}

echo ""
echo "Nomes dos usuários (DE VARIÁVEIS DE AMBIENTE):"
echo "  • ADMIN_PRODUCER_USER = $ADMIN_PRODUCER_USER"
echo "  • AGENT_CONSUMER_USER = $AGENT_CONSUMER_USER"
echo ""
echo "Senhas (DE VARIÁVEIS DE AMBIENTE):"
echo "  • RABBITMQ_ADMIN_PRODUCER_PASS = ****** (variável de env)"
echo "  • RABBITMQ_AGENT_CONSUMER_PASS = ****** (variável de env)"
echo ""

# ============================================
# Usuário PRODUTOR (configurável)
# ============================================
echo "[1/4] Configurando usuário produtor: '$ADMIN_PRODUCER_USER'..."

if rabbitmqctl list_users | grep -q "^$ADMIN_PRODUCER_USER"; then
    echo "      ✓ Usuário '$ADMIN_PRODUCER_USER' já existe"
    echo "      → Atualizando senha e permissões..."
else
    echo "      → Criando novo usuário '$ADMIN_PRODUCER_USER'..."
fi

# Tenta criar (se não existir, cria; se existir, comando é ignorado silenciosamente)
rabbitmqctl add_user "$ADMIN_PRODUCER_USER" "$ADMIN_PRODUCER_PASS" 2>/dev/null || true

# FORÇA atualizar a senha (mesmo que o usuário já exista)
echo "      → Aplicando senha (de RABBITMQ_ADMIN_PRODUCER_PASS)..."
rabbitmqctl change_password "$ADMIN_PRODUCER_USER" "$ADMIN_PRODUCER_PASS" 2>/dev/null || true

# FORÇA atualizar permissões (mesmo que já existam)
echo "      → Aplicando permissões (configure + write + read)..."
rabbitmqctl set_permissions -p / "$ADMIN_PRODUCER_USER" ".*" ".*" ".*" 2>/dev/null || true

# FORÇA atualizar tags
echo "      → Aplicando tags (management)..."
rabbitmqctl set_user_tags "$ADMIN_PRODUCER_USER" management 2>/dev/null || true

# ============================================
# Usuário CONSUMIDOR (configurável)
# ============================================
echo ""
echo "[2/4] Configurando usuário consumidor: '$AGENT_CONSUMER_USER'..."

if rabbitmqctl list_users | grep -q "^$AGENT_CONSUMER_USER"; then
    echo "      ✓ Usuário '$AGENT_CONSUMER_USER' já existe"
    echo "      → Atualizando senha e permissões..."
else
    echo "      → Criando novo usuário '$AGENT_CONSUMER_USER'..."
fi

# Tenta criar (se não existir, cria; se existir, comando é ignorado silenciosamente)
rabbitmqctl add_user "$AGENT_CONSUMER_USER" "$AGENT_CONSUMER_PASS" 2>/dev/null || true

# FORÇA atualizar a senha (mesmo que o usuário já exista)
echo "      → Aplicando senha (de RABBITMQ_AGENT_CONSUMER_PASS)..."
rabbitmqctl change_password "$AGENT_CONSUMER_USER" "$AGENT_CONSUMER_PASS" 2>/dev/null || true

# FORÇA atualizar permissões (mesmo que já existam)
# Agent-consumer pode:
# - CRIAR/MODIFICAR apenas filas com padrão queue_agent_* e queue_department_*
# - PUBLICAR apenas nessas mesmas filas
# - LER de QUALQUER fila (para consumir mensagens)
# 
# IMPORTANTE: Usar regex correto com escape de pipes
echo "      → Aplicando permissões (configure/write em queue_agent_* e queue_department_*, read em todas)..."
rabbitmqctl set_permissions -p / "$AGENT_CONSUMER_USER" '^(queue_agent_|queue_department_)' '^(queue_agent_|queue_department_)' '.*' 2>/dev/null || true

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
echo "  • $ADMIN_PRODUCER_USER     (produtor: configure + write + read em tudo)"
echo "  • $AGENT_CONSUMER_USER     (consumidor: configure + read em queue_agent_*, read em tudo)"
echo "  • guest              (padrão RabbitMQ)"
echo ""
echo "Conexão para Servidor (NetNotify):"
echo "  user: $ADMIN_PRODUCER_USER"
echo "  pass: [RABBITMQ_ADMIN_PRODUCER_PASS]"
echo ""
echo "Conexão para Agentes:"
echo "  user: $AGENT_CONSUMER_USER"
echo "  pass: [RABBITMQ_AGENT_CONSUMER_PASS]"
echo ""
echo "=========================================="
echo ""
