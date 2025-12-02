#!/bin/sh
# Script de healthcheck para RabbitMQ
# Verifica:
# 1. Se RabbitMQ responde com ping
# 2. Se os usuários segregados foram criados
# Retorna 0 (sucesso) apenas se ambas as condições forem atendidas

# Primeiro verifica ping
if ! rabbitmqctl ping > /dev/null 2>&1; then
    echo "RabbitMQ não respondendo a ping"
    exit 1
fi

# Verifica se admin-producer existe
if ! rabbitmqctl list_users 2>/dev/null | grep -q "^admin-producer"; then
    echo "Usuário admin-producer não encontrado"
    exit 1
fi

# Verifica se agent-consumer existe
if ! rabbitmqctl list_users 2>/dev/null | grep -q "^agent-consumer"; then
    echo "Usuário agent-consumer não encontrado"
    exit 1
fi

# Tudo OK
echo "RabbitMQ healthy: ping OK, usuários criados"
exit 0
