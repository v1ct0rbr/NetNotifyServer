# Como Funciona: Credenciais Segregadas no RabbitMQ

## O Fluxo de Inicialização

```
┌─────────────────────────────────────────────────────────────┐
│ docker-compose up -f docker-compose.dev.yaml               │
└─────────────────────────────────┬───────────────────────────┘
                                  │
                  ┌───────────────┴───────────────┐
                  │                               │
     ┌────────────▼──────────────┐    ┌──────────▼───────────────┐
     │ RabbitMQ Container        │    │ Variáveis de Ambiente    │
     │ (rabbitmq:3-management)   │    │ (docker-compose.yaml)    │
     └────────────┬──────────────┘    └──────────┬───────────────┘
                  │                               │
                  │ Lê RABBITMQ_DEFAULT_USER/PASS │
                  │ (reconhece nativamente)       │
                  │                               │
                  │    Executa                    │
                  │    /docker-entrypoint-init.d/ │
                  │                               │
     ┌────────────▼──────────────────────────────▼───────────┐
     │ rabbitmq-init.sh (nosso script customizado)          │
     │                                                       │
     │ ✓ Lê RABBITMQ_ADMIN_PRODUCER_PASS                   │
     │ ✓ Lê RABBITMQ_AGENT_CONSUMER_PASS                   │
     │                                                       │
     │ ✓ Cria user: admin-producer (write + configure)    │
     │ ✓ Cria user: agent-consumer (read only)             │
     └─────────────────────────────────────────────────────┘
```

## Exemplo de docker-compose.dev.yaml

```yaml
services:
  netnotify-rabbitmq:
    image: rabbitmq:3-management
    environment:
      # ✅ Reconhecidas NATIVAMENTE pelo RabbitMQ
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER}           # ex: guest
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}       # ex: guest
      RABBITMQ_DEFAULT_VHOST: ${RABBITMQ_VHOST}         # ex: /
      
      # ✅ Usadas apenas pelo script rabbitmq-init.sh
      RABBITMQ_ADMIN_PRODUCER_PASS: ${RABBITMQ_ADMIN_PRODUCER_PASS:-adminproducer123}
      RABBITMQ_AGENT_CONSUMER_PASS: ${RABBITMQ_AGENT_CONSUMER_PASS:-agentconsumer123}
    
    volumes:
      # ✅ Monta script que RabbitMQ executa automaticamente
      - ./rabbitmq-init.sh:/docker-entrypoint-init.d/rabbitmq-init.sh:ro
```

## O que o Script Faz

```bash
# 1. Aguarda RabbitMQ iniciar
sleep 5

# 2. Cria usuário PRODUTOR (servidor)
#    configure=".*"  ← Pode criar exchanges/queues
#    write=".*"      ← Pode publicar mensagens
#    read=""         ← NÃO consome
rabbitmqctl add_user admin-producer <senha>
rabbitmqctl set_permissions -p / admin-producer ".*" ".*" ""

# 3. Cria usuário CONSUMIDOR (agentes)
#    configure=""    ← NÃO cria exchanges/queues
#    write=""        ← NÃO publica
#    read=".*"       ← Pode consumir mensagens
rabbitmqctl add_user agent-consumer <senha>
rabbitmqctl set_permissions -p / agent-consumer "" "" ".*"
```

## Resumo: O que é Nativo vs Customizado

| Variável | Reconhecida por | Usado por |
|----------|-----------------|-----------|
| `RABBITMQ_DEFAULT_USER` | ✅ RabbitMQ nativo | Cria usuário padrão |
| `RABBITMQ_DEFAULT_PASS` | ✅ RabbitMQ nativo | Senha do usuário padrão |
| `RABBITMQ_DEFAULT_VHOST` | ✅ RabbitMQ nativo | Vhost padrão |
| `RABBITMQ_ADMIN_PRODUCER_PASS` | ❌ RabbitMQ ignora | Nosso script (rabbitmq-init.sh) |
| `RABBITMQ_AGENT_CONSUMER_PASS` | ❌ RabbitMQ ignora | Nosso script (rabbitmq-init.sh) |

## Verificação

Após iniciar o container:

```bash
# Ver todos os usuários criados
docker exec netnotify-rabbitmq rabbitmqctl list_users

# Resultado esperado:
# Listing users ...
# guest         [administrator]
# admin-producer [management]
# agent-consumer [management]
# user	tags

# Ver permissões de cada usuário
docker exec netnotify-rabbitmq rabbitmqctl list_permissions

# Resultado esperado:
# Listing permissions for vhost "/" ...
# user                  configure  write  read
# guest                 .*         .*     .*
# admin-producer        .*         .*     ""
# agent-consumer        ""         ""     .*
```

## Fluxo de Uso

```
┌─────────────────────────────┐
│ Servidor (NetNotify)        │
│ Usa: admin-producer         │
│ (write + configure)         │
│                             │
│ RabbitmqService:            │
│ - publishWithRoutingKey()   │
│ - publishToDepartment()     │
│ - basicPublish()            │
└──────────────┬──────────────┘
               │ (mensagens)
     ┌─────────▼──────────┐
     │ RabbitMQ Topic     │
     │ Exchange           │
     └─────────┬──────────┘
               │
     ┌─────────┴──────────┐
     │                    │
┌────▼──────────┐    ┌────▼──────────┐
│ Agente 1      │    │ Agente 2      │
│ (Financeiro)  │    │ (RH)          │
│               │    │               │
│ Usa:          │    │ Usa:          │
│ agent-consumer│    │ agent-consumer│
│ (read only)   │    │ (read only)   │
│               │    │               │
│ Consome de:   │    │ Consome de:   │
│ department.*  │    │ broadcast.*   │
│ broadcast.*   │    │ department.*  │
└───────────────┘    └───────────────┘
```

## Segurança

✅ **Servidor não pode consumir** (read="")
✅ **Agentes não podem publicar** (write="")
✅ **Agentes não podem criar/deletar** (configure="")
✅ **Isolamento por vhost** (todos no vhost "/")
✅ **Senhas customizáveis** (variáveis de ambiente)

## Próximos Passos (Recomendado)

1. **Em Desenvolvimento**: Use as senhas padrão
2. **Em Produção**: 
   - Altere as senhas via variáveis de ambiente
   - Remova o usuário `guest`
   - Habilite SSL/TLS na porta 5671
   - Configure auditoria de acessos
