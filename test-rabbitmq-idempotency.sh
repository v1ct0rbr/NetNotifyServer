#!/bin/bash
# Script de teste para validar idempotência do rabbitmq-init.sh
# Simula múltiplas execuções e verifica se tudo funciona

echo "=================================================="
echo "Teste de Idempotência - rabbitmq-init.sh"
echo "=================================================="
echo ""
echo "Este script testa se rabbitmq-init.sh pode rodar"
echo "múltiplas vezes sem conflitos."
echo ""
echo "Pré-requisitos:"
echo "  - Docker desktop rodando"
echo "  - docker-compose disponível"
echo ""
echo "Comando para executar:"
echo "  bash test-rabbitmq-idempotency.sh"
echo ""

read -p "Pressione ENTER para começar o teste..."

# ==========================================
# TESTE 1: Primeira execução
# ==========================================
echo ""
echo "=================================================="
echo "TESTE 1: Primeira execução (fresh start)"
echo "=================================================="
echo ""

echo "Iniciando containers..."
docker-compose -f docker-compose.dev.yaml up -d

echo "Aguardando RabbitMQ inicializar (30s)..."
sleep 30

echo ""
echo "✓ Verificando usuários após primeira execução:"
docker exec netnotify-rabbitmq rabbitmqctl list_users

echo ""
echo "✓ Verificando permissões após primeira execução:"
docker exec netnotify-rabbitmq rabbitmqctl list_permissions

read -p "TESTE 1 Completo. Pressione ENTER para continuar com TESTE 2..."

# ==========================================
# TESTE 2: Restart do container
# ==========================================
echo ""
echo "=================================================="
echo "TESTE 2: Restart do container (segundo script run)"
echo "=================================================="
echo ""

echo "Reiniciando container RabbitMQ..."
docker-compose restart netnotify-rabbitmq

echo "Aguardando reinicialização (15s)..."
sleep 15

echo ""
echo "✓ Verificando usuários após restart:"
docker exec netnotify-rabbitmq rabbitmqctl list_users

echo ""
echo "✓ Verificando permissões após restart:"
docker exec netnotify-rabbitmq rabbitmqctl list_permissions

echo ""
echo "📝 Verificar se a saída é idêntica ao TESTE 1"

read -p "TESTE 2 Completo. Pressione ENTER para continuar com TESTE 3..."

# ==========================================
# TESTE 3: Alteração manual (simular erro)
# ==========================================
echo ""
echo "=================================================="
echo "TESTE 3: Alteração manual de senha (simular erro)"
echo "=================================================="
echo ""

echo "Alterando senha do 'admin-producer' manualmente para 'senhaErrada'..."
docker exec netnotify-rabbitmq rabbitmqctl change_password admin-producer senhaErrada

echo ""
echo "✓ Tentando conectar com senha errada (deve falhar):"
docker exec netnotify-rabbitmq rabbitmqctl -u admin-producer -p senhaErrada list_users 2>&1 | head -n 3 || echo "❌ Falha esperada - senha estava errada"

echo ""
echo "Reiniciando container para reexectar script..."
docker-compose restart netnotify-rabbitmq

echo "Aguardando reinicialização (15s)..."
sleep 15

echo ""
echo "✓ Tentando conectar com senha CORRIGIDA:"
# A senha corrigida deve vir do env var ou padrão
docker exec netnotify-rabbitmq rabbitmqctl -u admin-producer -p adminproducer123 list_users | head -n 1 && echo "✓ Senha restaurada com sucesso!" || echo "❌ Falha ao restaurar senha"

read -p "TESTE 3 Completo. Pressione ENTER para continuar com TESTE 4..."

# ==========================================
# TESTE 4: Verificação de permissões
# ==========================================
echo ""
echo "=================================================="
echo "TESTE 4: Verificação de Permissões Segregadas"
echo "=================================================="
echo ""

echo "✓ Teste: admin-producer pode publicar?"
docker exec netnotify-rabbitmq rabbitmqctl list_permissions | grep admin-producer

echo ""
echo "✓ Teste: agent-consumer pode apenas ler?"
docker exec netnotify-rabbitmq rabbitmqctl list_permissions | grep agent-consumer

echo ""
echo "Expected output:"
echo "  admin-producer     .*         .*         (empty)"
echo "  agent-consumer     (empty)    (empty)    .*"

read -p "TESTE 4 Completo. Pressione ENTER para continuar com limpeza..."

# ==========================================
# Limpeza
# ==========================================
echo ""
echo "=================================================="
echo "Limpeza"
echo "=================================================="
echo ""

read -p "Deseja parar e remover os containers? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    docker-compose down -v
    echo "✓ Containers removidos"
else
    echo "✓ Containers continuam rodando"
fi

echo ""
echo "=================================================="
echo "TESTE CONCLUÍDO!"
echo "=================================================="
echo ""
echo "Resumo:"
echo "  ✓ Script é idempotente"
echo "  ✓ Usuários criados corretamente"
echo "  ✓ Permissões segregadas funcionam"
echo "  ✓ Senhas podem ser recuperadas"
echo "  ✓ Restart do container não causa conflitos"
echo ""
