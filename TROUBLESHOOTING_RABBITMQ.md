# 🔧 Troubleshooting - RabbitMQ Credenciais Segregadas

## ❌ Problema: "Connection refused"

**Sintoma**: `java.net.ConnectException: Connection refused`

**Causa**: Servidor tenta conectar antes do RabbitMQ estar ready

**Solução**:
```bash
# Aguarde o RabbitMQ estar healthy
docker-compose ps  # Deve mostrar netnotify-rabbitmq: Healthy

# Se ainda está iniciando (status "Healthy"), aguarde mais:
sleep 30

# Ou veja os logs:
docker logs netnotify-rabbitmq
```

---

## ❌ Problema: "PLAIN login refused: user 'admin-producer' - invalid credentials"

**Sintoma**: Erro de autenticação com admin-producer

**Causa**: 
1. Usuário não foi criado ainda (init.sh ainda não rodou)
2. Senha errada no .env

**Solução**:
```bash
# Verifique se usuário existe
docker exec netnotify-rabbitmq rabbitmqctl list_users

# Se não existe, aguarde o init.sh rodar (veja logs)
docker logs netnotify-rabbitmq | grep "Created user"

# Se existe com senha errada, force recreação:
docker exec netnotify-rabbitmq rabbitmqctl delete_user admin-producer
docker exec netnotify-rabbitmq rabbitmqctl add_user admin-producer adminproducer123
docker exec netnotify-rabbitmq rabbitmqctl set_permissions -p / admin-producer ".*" ".*" ".*"
```

---

## ❌ Problema: "ACCESS_REFUSED" para agent-consumer

**Sintoma**: Agente não consegue consumir mensagens

**Causa**: Permissões inadequadas ou tentando acessar queue errada

**Solução**:
```bash
# Verifique as permissões
docker exec netnotify-rabbitmq rabbitmqctl list_permissions

# Agent-consumer deve ter:
# configure: ^(queue_agent_|queue_department_)
# write:     ^(queue_agent_|queue_department_)
# read:      .*

# Se permissões estão erradas, corrija:
docker exec netnotify-rabbitmq rabbitmqctl set_permissions -p / agent-consumer \
  '^(queue_agent_|queue_department_)' \
  '^(queue_agent_|queue_department_)' \
  '.*'
```

---

## ❌ Problema: Servidor não inicializa (porta 8080 não abre)

**Sintoma**: Servidor trava durante inicialização

**Causa**: RabbitMQ ainda não está pronto (healthcheck ainda falhando)

**Solução**:
```bash
# Verifique o status
docker-compose ps

# Se RabbitMQ não está healthy, veja os logs
docker logs netnotify-rabbitmq | tail -100

# Procure por:
# - "✓ RabbitMQ está respondendo!" → OK
# - "✓ Configuração concluída com sucesso!" → OK
# - Erros de criação de usuário → PROBLEMA

# Se necessário, force restart:
docker-compose down -v
docker-compose up -d
```

---

## ❌ Problema: Exchange ou Queue não criada

**Sintoma**: Mensagens não sendo publicadas/consumidas

**Causa**: initializeExchangeAndQueue() falhou

**Solução**:
```bash
# Verifique os logs do servidor
docker logs netnotify-server | grep -i "exchange\|queue\|rabbitmq"

# Verifique se exchange existe
docker exec netnotify-rabbitmq rabbitmqctl list_exchanges | grep netnotify_topic

# Se não existe, recrie manualmente:
docker exec netnotify-rabbitmq rabbitmqctl declare_exchange netnotify_topic topic --durable=false
docker exec netnotify-rabbitmq rabbitmqctl declare_queue notifications --durable=true
docker exec netnotify-rabbitmq rabbitmqctl bind_queue_to_exchange notifications netnotify_topic '#'

# Reinicie o servidor
docker-compose restart netnotify-server
```

---

## ❌ Problema: Healthcheck fica "Unhealthy"

**Sintoma**: `docker-compose ps` mostra RabbitMQ como "Unhealthy"

**Causa**: 
1. RabbitMQ não respondendo a ping
2. Usuários não criados
3. init.sh com erro

**Solução**:
```bash
# Verifique os logs do init
docker logs netnotify-rabbitmq | grep -A 100 "Executando scripts"

# Teste o healthcheck manualmente
docker exec netnotify-rabbitmq /usr/local/bin/rabbitmq-healthcheck.sh

# Se falhar, verifique ping
docker exec netnotify-rabbitmq rabbitmqctl ping

# Verifique usuários
docker exec netnotify-rabbitmq rabbitmqctl list_users

# Se tudo está OK mas healthcheck falha, pode ser issue de timeout
# Aumente o start_period no docker-compose:
# start_period: 200s  # Aumente conforme necessário
```

---

## ⚠️ Problema: Performance Lenta no Startup

**Sintoma**: Leva muito tempo para iniciar

**Causa**: Timing muito apertado, init.sh demorando mais que esperado

**Solução**:
```bash
# Aumente o start_period no docker-compose.prod-test.yml:
healthcheck:
  test: ["CMD", "/usr/local/bin/rabbitmq-healthcheck.sh"]
  interval: 5s
  timeout: 10s
  retries: 30
  start_period: 180s  # Aumentado de 130s
```

---

## 🔍 Debug - Checklist de Saúde

Execute esta sequência para diagnosticar:

```bash
#!/bin/bash
echo "=== Docker Compose Status ==="
docker-compose ps

echo -e "\n=== RabbitMQ Ping ==="
docker exec netnotify-rabbitmq rabbitmqctl ping

echo -e "\n=== Usuários ==="
docker exec netnotify-rabbitmq rabbitmqctl list_users

echo -e "\n=== Permissões ==="
docker exec netnotify-rabbitmq rabbitmqctl list_permissions

echo -e "\n=== Exchanges ==="
docker exec netnotify-rabbitmq rabbitmqctl list_exchanges | grep netnotify

echo -e "\n=== Queues ==="
docker exec netnotify-rabbitmq rabbitmqctl list_queues

echo -e "\n=== Bindings ==="
docker exec netnotify-rabbitmq rabbitmqctl list_bindings

echo -e "\n=== Healthcheck ==="
docker exec netnotify-rabbitmq /usr/local/bin/rabbitmq-healthcheck.sh && echo "✓ OK" || echo "✗ FALHA"

echo -e "\n=== Logs (últimas 20 linhas) ==="
docker logs netnotify-server 2>&1 | tail -20
```

---

## 🛠️ Reset Total (Nuclear Option)

Se nada funcionar, reset total:

```bash
# Pare tudo
docker-compose -f docker-compose.prod-test.yml down -v

# Remova volumes (CUIDADO: Perde dados!)
docker volume prune -f

# Limpe imagens
docker image rm netnotifyserver-netnotify-rabbitmq -f

# Rebuild
docker-compose build --no-cache

# Reinicie
docker-compose up -d
```

---

## 📞 Informações Úteis

**RabbitMQ Management**: http://localhost:15673
- User: `admin`
- Pass: `admin`

**Porta RabbitMQ**: `5673` (externa) → `5672` (interna)

**Logs**:
```bash
docker logs netnotify-rabbitmq -f    # Follow logs
docker logs netnotify-server -f      # Follow server logs
```

**Acesso ao Container**:
```bash
docker exec -it netnotify-rabbitmq sh
docker exec -it netnotify-server sh
```

---

**Última Atualização**: 2025-12-02
**Status**: ✅ Operacional
