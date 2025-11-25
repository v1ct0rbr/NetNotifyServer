# RabbitMQ - Credenciais Segregadas para Produtor e Consumidor

## Visão Geral

O servidor **NetNotify** utiliza credenciais separadas no RabbitMQ para garantir princípio de menor privilégio:

- **Produtor (Servidor NetNotify)**: `admin-producer` - permissão de **write** (publicar mensagens)
- **Consumidor (Agentes)**: `agent-consumer` - permissão de **read** (apenas consumir mensagens)

## Credenciais Padrão

### Servidor (Produtor)
```
Username: admin-producer
Password: adminproducer123 (configurável via RABBITMQ_ADMIN_PRODUCER_PASS)
Permissões: configure, write (criar exchanges/queues e publicar)
```

### Agentes (Consumidor)
```
Username: agent-consumer
Password: agentconsumer123 (configurável via RABBITMQ_AGENT_CONSUMER_PASS)
Permissões: read (apenas consumir mensagens)
```

## Configuração no Docker Compose

Os usuários são criados automaticamente no container RabbitMQ via script de inicialização (`rabbitmq-init.sh`).

### Desenvolvimento (docker-compose.dev.yaml)
```yaml
environment:
  RABBITMQ_ADMIN_PRODUCER_PASS: ${RABBITMQ_ADMIN_PRODUCER_PASS:-adminproducer123}
  RABBITMQ_AGENT_CONSUMER_PASS: ${RABBITMQ_AGENT_CONSUMER_PASS:-agentconsumer123}
volumes:
  - ./rabbitmq-init.sh:/docker-entrypoint-init.d/rabbitmq-init.sh:ro
```

### Produção (docker-compose.yaml)
```yaml
environment:
  RABBITMQ_ADMIN_PRODUCER_PASS: ${RABBITMQ_ADMIN_PRODUCER_PASS:-adminproducer123}
  RABBITMQ_AGENT_CONSUMER_PASS: ${RABBITMQ_AGENT_CONSUMER_PASS:-agentconsumer123}
```

## Configuração do Servidor (application.yaml)

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: ${RABBITMQ_USER:guest}  # Ou use admin-producer para produção
    password: ${RABBITMQ_PASSWORD:guest}
    virtual-host: /
    # Credenciais do produtor
    admin-producer-username: ${RABBITMQ_ADMIN_PRODUCER_USER:admin-producer}
    admin-producer-password: ${RABBITMQ_ADMIN_PRODUCER_PASS:adminproducer123}
    # Credenciais do consumidor (para testes)
    agent-consumer-username: ${RABBITMQ_AGENT_CONSUMER_USER:agent-consumer}
    agent-consumer-password: ${RABBITMQ_AGENT_CONSUMER_PASS:agentconsumer123}
```

## Configuração de Agentes

Quando configurar um agente externo para consumir mensagens, use:

```
Host: <seu-rabbitmq-host>
Port: 5672
Virtual Host: /
Username: agent-consumer
Password: agentconsumer123
```

### Exemplo de Conexão em Python
```python
import pika

credentials = pika.PlainCredentials('agent-consumer', 'agentconsumer123')
parameters = pika.ConnectionParameters('localhost', 5672, '/', credentials)
connection = pika.BlockingConnection(parameters)
channel = connection.channel()

# Declarar queues (agente possui permissão read-only)
channel.queue_declare(queue='broadcast.queue', durable=True)
channel.queue_declare(queue='department.financeiro', durable=True)

# Consumir mensagens
channel.basic_consume(queue='broadcast.queue', on_message_callback=callback, auto_ack=True)
channel.start_consuming()
```

### Exemplo de Conexão em Node.js/JavaScript
```javascript
const amqp = require('amqplib');

async function consumeMessages() {
  const connection = await amqp.connect({
    hostname: 'localhost',
    port: 5672,
    username: 'agent-consumer',
    password: 'agentconsumer123',
    vhost: '/'
  });
  
  const channel = await connection.createChannel();
  
  // Declarar queues (agente possui permissão read-only)
  await channel.assertQueue('broadcast.queue', { durable: true });
  await channel.assertQueue('department.financeiro', { durable: true });
  
  // Consumir mensagens
  channel.consume('broadcast.queue', (msg) => {
    if (msg) {
      console.log('Message:', msg.content.toString());
      channel.ack(msg);
    }
  });
}

consumeMessages().catch(console.error);
```

### Exemplo de Conexão em Java
```java
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;

public class AgentConsumer {
    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setVirtualHost("/");
        factory.setUsername("agent-consumer");
        factory.setPassword("agentconsumer123");
        
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            
            // Declarar queues (agente possui permissão read-only)
            channel.queueDeclare("broadcast.queue", true, false, false, null);
            channel.queueDeclare("department.financeiro", true, false, false, null);
            
            // Consumir mensagens
            channel.basicConsume("broadcast.queue", true, (tag, delivery) -> {
                String message = new String(delivery.getBody());
                System.out.println("Message: " + message);
            }, tag -> {});
        }
    }
}
```

## Gerenciamento de Credenciais

### Alterar Senha do Produtor
```bash
docker exec netnotify-rabbitmq rabbitmqctl change_password admin-producer <nova-senha>
```

### Alterar Senha do Consumidor
```bash
docker exec netnotify-rabbitmq rabbitmqctl change_password agent-consumer <nova-senha>
```

### Visualizar Usuários e Permissões
```bash
docker exec netnotify-rabbitmq rabbitmqctl list_users
docker exec netnotify-rabbitmq rabbitmqctl list_permissions
```

## Segurança - Boas Práticas

1. **Alterar credenciais padrão em produção**
   - Usar variáveis de ambiente seguras (secrets no Kubernetes, Vault, etc.)
   
2. **Remover usuário guest**
   - Descomentar linha no `rabbitmq-init.sh` para remover usuário padrão

3. **Usar SSL/TLS**
   - Configurar porta 5671 com certificados SSL para comunicação criptografada

4. **Monitorar acessos**
   - Habilitar auditoria no RabbitMQ para rastrear operações

5. **Rotação de senhas**
   - Implementar rotação periódica de credenciais

## Troubleshooting

### Agente não consegue se conectar
```
Error: ACCESS-REFUSED - operation not permitted
```
**Solução**: Verificar se o agente está usando as credenciais corretas (`agent-consumer`) e se as permissões foram definidas corretamente.

### Agente consegue publicar (não deveria)
```bash
# Verificar permissões
docker exec netnotify-rabbitmq rabbitmqctl list_user_permissions agent-consumer
```
**Esperado**: `configure=""` e `write=""` (vazio)

### Servidor não consegue publicar
```bash
# Verificar permissões do produtor
docker exec netnotify-rabbitmq rabbitmqctl list_user_permissions admin-producer
```
**Esperado**: `configure=".*"` e `write=".*"`
