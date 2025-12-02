#!/bin/bash
# Script de inicialização para configurar usuários segregados no RabbitMQ
# Cria dois usuários com permissões específicas:
# - admin-producer: permissões de write+configure (server publica mensagens)
# - agent-consumer: permissões read-only (agentes consomem mensagens)

set -e

echo "=========================================="
echo "Configurando usuários segregados RabbitMQ"
echo "=========================================="

# Importa credenciais do ambiente
ADMIN_PRODUCER_USER="${RABBITMQ_ADMIN_PRODUCER_USER:-admin-producer}"
ADMIN_PRODUCER_PASS="${RABBITMQ_ADMIN_PRODUCER_PASS:-adminproducer123}"
AGENT_CONSUMER_USER="${RABBITMQ_AGENT_CONSUMER_USER:-agent-consumer}"
AGENT_CONSUMER_PASS="${RABBITMQ_AGENT_CONSUMER_PASS:-agentconsumer123}"

echo "Usuários a configurar:"
echo "  - Producer: $ADMIN_PRODUCER_USER"
echo "  - Consumer: $AGENT_CONSUMER_USER"

# Verifica se o usuário admin-producer já existe
if rabbitmqctl list_users | grep -q "^$ADMIN_PRODUCER_USER"; then
    echo "✓ Usuário '$ADMIN_PRODUCER_USER' já existe"
else
    echo "Criando usuário '$ADMIN_PRODUCER_USER'..."
    rabbitmqctl add_user "$ADMIN_PRODUCER_USER" "$ADMIN_PRODUCER_PASS" || {
        exit_code=$?
        if [ $exit_code -eq 70 ]; then
            echo "✓ Usuário já existe (code 70)"
        else
            echo "❌ Erro ao criar usuário: $exit_code"
            exit $exit_code
        fi
    }
    echo "✓ Usuário criado com sucesso"
fi

# Configura permissões do admin-producer (write e configure em tudo)
echo "Configurando permissões para '$ADMIN_PRODUCER_USER'..."
rabbitmqctl set_permissions -p "/" "$ADMIN_PRODUCER_USER" ".*" ".*" ".*" || {
    echo "⚠ Erro ao configurar permissões para producer (continuando...)"
}
echo "✓ Permissões configuradas"

# Verifica se o usuário agent-consumer já existe
if rabbitmqctl list_users | grep -q "^$AGENT_CONSUMER_USER"; then
    echo "✓ Usuário '$AGENT_CONSUMER_USER' já existe"
else
    echo "Criando usuário '$AGENT_CONSUMER_USER'..."
    rabbitmqctl add_user "$AGENT_CONSUMER_USER" "$AGENT_CONSUMER_PASS" || {
        exit_code=$?
        if [ $exit_code -eq 70 ]; then
            echo "✓ Usuário já existe (code 70)"
        else
            echo "❌ Erro ao criar usuário: $exit_code"
            exit $exit_code
        fi
    }
    echo "✓ Usuário criado com sucesso"
fi

# Configura permissões do agent-consumer (read-only)
echo "Configurando permissões para '$AGENT_CONSUMER_USER'..."
rabbitmqctl set_permissions -p "/" "$AGENT_CONSUMER_USER" "" "" ".*" || {
    echo "⚠ Erro ao configurar permissões para consumer (continuando...)"
}
echo "✓ Permissões configuradas"

# Lista usuários criados para verificação
echo ""
echo "Usuários configurados:"
rabbitmqctl list_users | grep -E "($ADMIN_PRODUCER_USER|$AGENT_CONSUMER_USER)" || true

echo ""
echo "=========================================="
echo "✓ Configuração de usuários concluída!"
echo "=========================================="
