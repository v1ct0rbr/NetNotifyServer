#!/bin/bash
# Script de inicialização do RabbitMQ com usuários segregados
# Este script é executado DENTRO do container RabbitMQ após inicialização
# Usa as variáveis de ambiente RABBITMQ_ADMIN_PRODUCER_PASS e RABBITMQ_AGENT_CONSUMER_PASS
# 
# IMPORTANTE: O RabbitMQ nativo reconhece RABBITMQ_DEFAULT_USER/PASS
# Este script cria usuários ADICIONAIS com permissões segregadas
#
# Fluxo:
# 1. RabbitMQ inicia com user padrão (guest/guest ou conforme RABBITMQ_DEFAULT_USER/PASS)
# 2. Este script é executado automaticamente (/docker-entrypoint-init.d/)
# 3. Cria dois usuários adicionais: admin-producer e agent-consumer

set -e

# Aguarda RabbitMQ iniciar completamente
sleep 5

echo "=========================================="
echo "Criando usuários segregados no RabbitMQ..."
echo "=========================================="

# Cria usuário produtor (admin-producer)
# Permissões: configure=".*" (criar exchanges/queues), write=".*" (publicar), read="" (não consome)
echo "Criando usuário 'admin-producer' com permissões de PRODUTOR (write + configure)..."
rabbitmqctl add_user admin-producer ${RABBITMQ_ADMIN_PRODUCER_PASS:-adminproducer123} || true
rabbitmqctl set_permissions -p / admin-producer ".*" ".*" "" || true
rabbitmqctl set_user_tags admin-producer management || true

# Cria usuário consumidor (agent-consumer)
# Permissões: configure="" (não cria), write="" (não publica), read=".*" (consome mensagens)
echo "Criando usuário 'agent-consumer' com permissões de CONSUMIDOR (read-only)..."
rabbitmqctl add_user agent-consumer ${RABBITMQ_AGENT_CONSUMER_PASS:-agentconsumer123} || true
rabbitmqctl set_permissions -p / agent-consumer "" "" ".*" || true

echo "=========================================="
echo "Usuários criados com sucesso!"
echo "=========================================="
echo ""
echo "Usuários disponíveis:"
echo "  1. admin-producer     (write + configure)"
echo "  2. agent-consumer     (read only)"
echo "  3. guest              (padrão do RabbitMQ - REMOVER EM PRODUÇÃO)"
echo ""
rabbitmqctl list_users
echo ""
echo "Permissões (read, write, configure):"
rabbitmqctl list_permissions
echo ""
