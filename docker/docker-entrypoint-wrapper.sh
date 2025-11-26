#!/bin/sh
# Wrapper entrypoint para RabbitMQ que executa scripts em /docker-entrypoint-init.d/
# Este script:
# 1. Inicia RabbitMQ em background
# 2. Aguarda RabbitMQ estar pronto
# 3. Executa scripts em /docker-entrypoint-init.d/
# 4. Mantém RabbitMQ em foreground

set -e

# Inicia RabbitMQ em background
echo "Iniciando RabbitMQ..."
"$@" &
RABBITMQ_PID=$!

# Aguarda RabbitMQ ficar TOTALMENTE pronto (máximo 120 segundos)
echo "Aguardando RabbitMQ ficar pronto..."
attempt=0
max_attempts=120

while [ $attempt -lt $max_attempts ]; do
    if rabbitmqctl ping 2>/dev/null >/dev/null; then
        echo "✓ RabbitMQ está respondendo!"
        break
    fi
    attempt=$((attempt + 1))
    if [ $((attempt % 10)) -eq 0 ]; then
        echo "  (tentativa $attempt/$max_attempts)"
    fi
    sleep 1
done

if [ $attempt -eq $max_attempts ]; then
    echo "❌ RabbitMQ não ficou pronto após 120 segundos"
    kill $RABBITMQ_PID 2>/dev/null || true
    exit 1
fi

# Aguarda adicional de 10 segundos para garantir estabilidade total
echo "Aguardando estabilização final..."
sleep 10

# Executa scripts em /docker-entrypoint-init.d/ em ordem alfabética
if [ -d /docker-entrypoint-init.d/ ]; then
    echo "=========================================="
    echo "Executando scripts de inicialização..."
    echo "=========================================="
    for script in /docker-entrypoint-init.d/*.sh; do
        if [ -f "$script" ] && [ -x "$script" ]; then
            echo ""
            echo "Executando: $(basename $script)"
            if "$script"; then
                echo "✓ $(basename $script) concluído com sucesso"
            else
                exit_code=$?
                echo "⚠ $(basename $script) retornou código $exit_code (continuando...)"
            fi
        fi
    done
    echo ""
fi

echo "=========================================="
echo "Iniciação de scripts concluída!"
echo "=========================================="

# Aguarda que RabbitMQ continue rodando
wait $RABBITMQ_PID
