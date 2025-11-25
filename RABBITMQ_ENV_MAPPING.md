# RabbitMQ - Mapeamento: Hardcoded vs Variáveis de Ambiente

## Resumo Executivo

```
┌──────────────────────────────┐
│ NOMES DOS USUÁRIOS (FIXO)    │
│ ✗ admin-producer             │
│ ✗ agent-consumer             │
│ (não são variáveis)          │
└──────────────────────────────┘
           ▲
           │ usam
           │
┌──────────────────────────────┐
│ SENHAS (VARIÁVEIS DE ENV)    │
│ ✓ RABBITMQ_ADMIN_PRODUCER_PASS    │
│ ✓ RABBITMQ_AGENT_CONSUMER_PASS    │
│ (são variáveis de ambiente)  │
└──────────────────────────────┘
```

## Detalhamento

### O que é HARDCODED (não muda)

| Componente | Valor | Local |
|-----------|-------|-------|
| Nome usuário produtor | `admin-producer` | rabbitmq-init.sh linha 41 |
| Nome usuário consumidor | `agent-consumer` | rabbitmq-init.sh linha 65 |
| Permissões produtor | `".*" ".*" ""` | rabbitmq-init.sh linha 54 |
| Permissões consumidor | `"" "" ".*"` | rabbitmq-init.sh linha 78 |
| Vhost | `/` | rabbitmq-init.sh linhas 54, 78 |

### O que é VARIÁVEL (pode mudar via env)

| Variável | Default | Usado em |
|----------|---------|----------|
| `RABBITMQ_ADMIN_PRODUCER_PASS` | `adminproducer123` | Senha do produtor |
| `RABBITMQ_AGENT_CONSUMER_PASS` | `agentconsumer123` | Senha do consumidor |

## Exemplos de Uso

### Desenvolvimento (usar defaults)
```bash
# Sem variáveis - usa padrão
docker-compose -f docker-compose.dev.yaml up

# Resultado:
# user: admin-producer
# pass: adminproducer123 (default)
```

### Produção (alterar apenas as senhas)
```bash
# Variáveis de ambiente com senhas fortes
export RABBITMQ_ADMIN_PRODUCER_PASS="SenhaForte@Producer#2024"
export RABBITMQ_AGENT_CONSUMER_PASS="SenhaForte@Consumer#2024"

docker-compose up

# Resultado:
# user: admin-producer
# pass: SenhaForte@Producer#2024 (da variável)
```

### Via arquivo .env
```bash
# .env
RABBITMQ_ADMIN_PRODUCER_PASS=SenhaForte@Producer#2024
RABBITMQ_AGENT_CONSUMER_PASS=SenhaForte@Consumer#2024

# Executar:
docker-compose up
```

### Via docker-compose override
```bash
# docker-compose.override.yaml
services:
  netnotify-rabbitmq:
    environment:
      RABBITMQ_ADMIN_PRODUCER_PASS: SenhaForte@Producer#2024
      RABBITMQ_AGENT_CONSUMER_PASS: SenhaForte@Consumer#2024

# Executar:
docker-compose up
```

## Script - Fluxo Completo

```bash
#!/bin/bash

# 1. Lê variáveis de ambiente (com fallback para padrão)
ADMIN_PRODUCER_PASS=${RABBITMQ_ADMIN_PRODUCER_PASS:-adminproducer123}
AGENT_CONSUMER_PASS=${RABBITMQ_AGENT_CONSUMER_PASS:-agentconsumer123}

# 2. Cria usuário com NOME HARDCODED + SENHA VARIÁVEL
rabbitmqctl add_user admin-producer "$ADMIN_PRODUCER_PASS"
#                     ▲▲▲▲▲▲▲▲▲▲   ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲
#                     FIXO          VARIÁVEL

# 3. Mesma coisa para consumidor
rabbitmqctl add_user agent-consumer "$AGENT_CONSUMER_PASS"
#                     ▲▲▲▲▲▲▲▲▲▲    ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲
#                     FIXO           VARIÁVEL
```

## Código-Fonte no Script

```bash
# Linhas 28-29: Lê variáveis com fallback para padrão
ADMIN_PRODUCER_PASS=${RABBITMQ_ADMIN_PRODUCER_PASS:-adminproducer123}
AGENT_CONSUMER_PASS=${RABBITMQ_AGENT_CONSUMER_PASS:-agentconsumer123}

# Linha 41: Nome hardcoded + senha da variável
rabbitmqctl add_user admin-producer "$ADMIN_PRODUCER_PASS"
                      ▲▲▲▲▲▲▲▲▲▲ (FIXO)  ▲ (VARIÁVEL)

# Linha 65: Nome hardcoded + senha da variável
rabbitmqctl add_user agent-consumer "$AGENT_CONSUMER_PASS"
                      ▲▲▲▲▲▲▲▲ (FIXO)   ▲ (VARIÁVEL)
```

## Por Quê Dessa Forma?

| Aspecto | Por quê Hardcoded | Por quê Variável |
|--------|-------------------|-----------------|
| **Nomes dos usuários** | Conhecidos na aplicação (RabbitmqService.java) | Não faria sentido mudar |
| **Senhas** | Inseguro hardcoded | Devem ser secretas e customizáveis |

### Código Java usa nomes fixos:
```java
// RabbitmqService.java
@Value("${spring.rabbitmq.admin-producer-username:admin-producer}")
private String adminProducerUsername;  // Usa valor fixo padrão

@Value("${spring.rabbitmq.admin-producer-password:adminproducer123}")
private String adminProducerPassword;  // Usa valor variável
```

## Resumo Rápido

✅ **Nomes são FIXOS** porque:
- Estão hardcoded na aplicação Java
- Não faz sentido mudar em deploy
- Conhecidos em tempo de design

✅ **Senhas são VARIÁVEIS** porque:
- Devem ser secretas
- Diferentes por ambiente (dev/prod)
- Nunca devem estar hardcoded

## Checklist para Produção

```
☑ Alterar RABBITMQ_ADMIN_PRODUCER_PASS para senha forte
☑ Alterar RABBITMQ_AGENT_CONSUMER_PASS para senha forte
☑ Remover usuário guest (descomentar em rabbitmq-init.sh)
☑ Habilitar SSL/TLS (porta 5671)
☑ Configurar credenciais como secrets (Kubernetes, Docker Secrets, etc.)
☑ Não committar senhas no .env no git
☑ Usar .env.local ou .env.production localmente
```

## Comando para Mudar Senha em Produção

```bash
# Se precisar alterar a senha APÓS o deployment
docker exec netnotify-rabbitmq \
  rabbitmqctl change_password admin-producer <nova-senha>

docker exec netnotify-rabbitmq \
  rabbitmqctl change_password agent-consumer <nova-senha>
```

## Visualização Final

```
┌─────────────────────────────────────────────────────┐
│ docker-compose.dev.yaml                             │
│                                                     │
│ environment:                                        │
│   RABBITMQ_ADMIN_PRODUCER_PASS: ${...:-default}    │
│   RABBITMQ_AGENT_CONSUMER_PASS: ${...:-default}    │
└────────────────┬────────────────────────────────────┘
                 │ passa para container
                 ▼
┌─────────────────────────────────────────────────────┐
│ rabbitmq-init.sh (dentro do container)              │
│                                                     │
│ ADMIN_PRODUCER_PASS=${RABBITMQ_..:-adminproducer} │
│                                                     │
│ rabbitmqctl add_user admin-producer $PASS  ← FIXO │
│                                                     │
│ # Resultado:                                        │
│ # User: admin-producer (fixo)                       │
│ # Pass: valor da variável (ou default)              │
└─────────────────────────────────────────────────────┘
```

## TL;DR

- 🔒 **Nomes**: `admin-producer` e `agent-consumer` (FIXOS no código)
- 🔐 **Senhas**: via `RABBITMQ_*_PASS` (VARIÁVEIS de ambiente)
- 📌 **Default**: `adminproducer123` e `agentconsumer123` (para dev)
- 🚀 **Produção**: Alterar via variáveis de ambiente (secrets/vault)
