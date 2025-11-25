# Segregação de Credenciais RabbitMQ - Guia Rápido

## O Problema

Anteriormente, servidor e agentes usavam as mesmas credenciais, comprometendo a segurança:
- Um agente comprometido poderia publicar mensagens
- Não havia isolamento de permissões

## A Solução

Agora temos **dois usuários com permissões segregadas**:

### 1. **Servidor (NetNotify)** - `admin-producer`
- ✅ Cria exchanges e queues
- ✅ Publica mensagens
- ❌ Não consome (apenas produtor)

### 2. **Agentes** - `agent-consumer`
- ❌ Não cria exchanges/queues
- ❌ Não publica mensagens
- ✅ Apenas consome mensagens

## Setup Rápido

### Passo 1: Iniciar com Docker Compose
```bash
# Desenvolvimento (auto-cria usuários)
docker-compose -f docker-compose.dev.yaml up

# Produção (variáveis de ambiente)
export RABBITMQ_ADMIN_PRODUCER_PASS=senhaForte123
export RABBITMQ_AGENT_CONSUMER_PASS=outroSegredo456
docker-compose up
```

### Passo 2: Verificar Usuários
```bash
docker exec netnotify-rabbitmq rabbitmqctl list_users
docker exec netnotify-rabbitmq rabbitmqctl list_permissions
```

### Passo 3: Configurar Agente
Use as credenciais do consumidor:
```
Host: localhost:5672
Username: agent-consumer
Password: agentconsumer123
```

## Mudanças no Código

### RabbitmqService.java
- ✅ `rabbitConnectionFactoryProducer()` - credenciais do servidor
- ✅ `rabbitConnectionFactoryConsumer()` - credenciais dos agentes
- ✅ Todos os `publish*()` methods usam factory do produtor

### application.yaml
```yaml
spring.rabbitmq.admin-producer-username: admin-producer
spring.rabbitmq.admin-producer-password: adminproducer123
spring.rabbitmq.agent-consumer-username: agent-consumer
spring.rabbitmq.agent-consumer-password: agentconsumer123
```

### .env.example
```dotenv
RABBITMQ_ADMIN_PRODUCER_PASS=adminproducer123
RABBITMQ_AGENT_CONSUMER_PASS=agentconsumer123
```

## Próximas Etapas Recomendadas

1. **Em Produção**: Alterar senhas via environment variables
2. **SSL/TLS**: Usar porta 5671 com certificados
3. **Auditoria**: Habilitar logs de acesso RabbitMQ
4. **Rotation**: Implementar rotação periódica de credenciais

## Mais Informações

Veja `RABBITMQ_CREDENTIALS.md` para documentação completa com exemplos de agentes em Python, Node.js e Java.
