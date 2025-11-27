# Análise e Correção: Erro ACCESS_REFUSED - Agent Consumer

## 🔴 Problema Identificado

Mensagem de erro no agente:
```
? Tentando reconectar em 5s...
? Connecting to localhost:5672...
? Erro de I/O: null
  Tipo: java.io.IOException
  Causa: channel error; protocol method: #method<channel.close>
         (reply-code=403, reply-text=ACCESS_REFUSED - configure access to 
         exchange 'netnotify_topic' in vhost '/' refused for user 
         'agent-consumer', class-id=40, method-id=10)
```

### Causa Raiz

O agente está tentando **DECLARAR** o exchange `netnotify_topic`, mas o usuário `agent-consumer` só tem permissão de **READ** (consumir mensagens), não de **CONFIGURE** (criar/modificar infraestrutura).

```
Permissões do agent-consumer:
  configure: (vazio)     ❌ Não pode declarar/modificar exchanges/filas
  write:     (vazio)     ❌ Não pode publicar mensagens
  read:      .*          ✅ Pode consumir mensagens
```

---

## ✅ Solução Implementada

### 1. **No Servidor (RabbitmqService.java)**

Modificar métodos para usar **Declarações Passivas** quando consumindo:

#### ❌ ANTES (Ativo - tenta criar):
```java
// Tenta DECLARAR o exchange (cria se não existir)
channel.exchangeDeclare(fanoutExchange.getName(), fanoutExchange.getType(), true);
channel.queueDeclare(queueName, true, false, false, null);
```

#### ✅ DEPOIS (Passivo - apenas verifica):
```java
// Apenas VERIFICA se o exchange existe (não cria)
try {
    channel.exchangeDeclarePassive(fanoutExchange.getName());
} catch (IOException e) {
    System.err.println("Exchange does not exist: " + e.getMessage());
    throw e;
}
```

### Métodos Corrigidos no Servidor:

1. **`basicConsume(long timeoutMillis)`**
   - ✅ Usa `rabbitConnectionFactoryConsumer()` (agent-consumer)
   - ✅ Verifica exchange com `exchangeDeclarePassive()`
   - ✅ Verifica fila com `queueDeclarePassive()`
   - ❌ NÃO tenta fazer bind (apenas consome de fila existente)

2. **`listQueuesForCurrentChannel()`**
   - ✅ Usa `rabbitConnectionFactoryProducer()` (admin-producer)
   - ✅ Verifica exchange com `exchangeDeclarePassive()`
   - ✅ Pode acessar informações de filas

3. **`consumeFromDepartmentQueue(String queueName, long timeoutMillis)`**
   - ✅ Usa `rabbitConnectionFactoryConsumer()` (agent-consumer)
   - ✅ Verifica fila com `queueDeclarePassive()`
   - ❌ NÃO tenta fazer bind

### 2. **No Agente (RabbitmqService.java)**

O código fornecido já está **CORRETO**, mas com melhorias importantes:

#### Pontos de Atenção:

```java
// ⚠️ RISCO: Esta linha tenta DECLARAR o exchange
channel.exchangeDeclare(exchangeName, "topic", false);  // ❌ PROBLEMA

// ✅ SOLUÇÃO: Usar declaração passiva
try {
    channel.exchangeDeclarePassive(exchangeName);  // ✅ Apenas verifica
} catch (IOException e) {
    System.err.println("Exchange does not exist!");
    throw e;
}
```

---

## 📋 Arquitetura Revisada

### Responsabilidades

```
┌─────────────────────────────────────────────────────────────────┐
│                         SERVIDOR                                │
│  (NetNotify Backend - usa admin-producer)                       │
├─────────────────────────────────────────────────────────────────┤
│ • Cria Exchange 'netnotify_topic' (topic, não-durável)          │
│ • Publica mensagens com routing keys:                           │
│   - broadcast.general    (para TODOS)                           │
│   - department.financeiro (para Financeiro)                     │
│   - department.rh        (para RH)                              │
│                                                                 │
│ Permissões: admin-producer                                      │
│  • Configure: .*  (pode criar exchanges/filas)                 │
│  • Write:     .*  (pode publicar)                              │
│  • Read:           (não consome)                               │
└─────────────────────────────────────────────────────────────────┘
                              ⬇️
                         (Topic Exchange)
                              ⬆️
┌─────────────────────────────────────────────────────────────────┐
│                        AGENTE 1                                  │
│  (Desktop/Desktop) - usa agent-consumer                         │
├─────────────────────────────────────────────────────────────────┤
│ • Consome de 2 filas:                                           │
│   - queue_general_{hostname}                                    │
│     └─ Binding: broadcast.* → recebe TODOS os broadcasts       │
│   - queue_department_financeiro_{hostname}                      │
│     └─ Binding: department.financeiro → recebe mensagens de     │
│        Financeiro                                                │
│                                                                 │
│ Permissões: agent-consumer                                      │
│  • Configure:      (não pode criar/modificar)                 │
│  • Write:          (não pode publicar)                         │
│  • Read:      .*   (pode consumir)                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔧 Fluxo de Funcionamento Correto

### 1️⃣ **Inicialização do Servidor**

```
RabbitMQ inicia
  ↓
admin-producer conecta (permissão write + configure)
  ↓
RabbitConfig.initializeExchangeAndQueue() executa:
  • Declara Exchange 'netnotify_topic' (tipo: topic, não-durável)
  • Cria Fila 'notification_queue' (durável)
  • Faz Bind com padrão '#' (recebe TUDO para testes)
  ↓
Servidor está pronto para publicar mensagens
```

### 2️⃣ **Inicialização do Agente**

```
Agent inicia
  ↓
agent-consumer conecta (permissão read-only)
  ↓
RabbitmqService.setupQueueAndExchangeConsumer() executa:
  • Verifica Exchange 'netnotify_topic' (PASSIVO - não cria)
  • Cria Fila 'queue_general_{hostname}' (sem problema - é primeira)
  • Faz Bind: broadcast.* → recebe mensagens gerais
  • Cria Fila 'queue_department_financeiro_{hostname}' (sem problema)
  • Faz Bind: department.financeiro → recebe mensagens de Financeiro
  ↓
Agente está pronto para consumir mensagens
```

### 3️⃣ **Fluxo de Mensagem**

```
Servidor publica: "Atenção: Pagamento Aprovado" com departamento=Financeiro
  ↓
Routing Key: department.financeiro
  ↓
Topic Exchange 'netnotify_topic' roteia para:
  • Fila 'queue_department_financeiro_server'      (agente de desktop)
  • Fila 'queue_department_financeiro_notebook'    (agente de notebook)
  • ... todas as outras filas que batem no padrão 'department.financeiro'
  ↓
Cada agente consome sua mensagem e exibe alerta
```

---

## ⚙️ Configuração Necessária

### settings.properties do Agente

```properties
# Credenciais - DEVE usar agent-consumer
rabbitmq.host=localhost
rabbitmq.port=5672
rabbitmq.username=agent-consumer
rabbitmq.password=agentconsumer123
rabbitmq.virtualhost=/
rabbitmq.exchange=netnotify_topic

# Informações do Agente
agent.department.name=Financeiro
agent.hostname=server-desktop-01
```

### Permissões no RabbitMQ

```bash
# Criar usuário agent-consumer (se não existir)
docker exec netnotify-rabbitmq rabbitmqctl add_user agent-consumer agentconsumer123

# Definir permissões (read-only)
docker exec netnotify-rabbitmq rabbitmqctl set_permissions -p / agent-consumer "" "" ".*"

# Verificar
docker exec netnotify-rabbitmq rabbitmqctl list_permissions
```

---

## 🧪 Testes para Validar

### 1. Verificar Permissões
```bash
docker exec netnotify-rabbitmq rabbitmqctl list_permissions

# Saída esperada:
# user              configure  write  read
# admin-producer    .*         .*     
# agent-consumer                      .*
# admin             .*         .*     .*
```

### 2. Testar Consumo do Agente
```bash
# No agente, logs devem mostrar:
✓ Exchange existe: netnotify_topic (tipo: topic)
✓ Fila Geral criada: queue_general_desktop-01
  → Binding Pattern: broadcast.*
  → Consumindo: SIM
✓ Fila Departamento criada: queue_department_financeiro_desktop-01
  → Binding Pattern: department.financeiro
  → Consumindo: SIM
✓ Conectado! Aguardando mensagens...
```

### 3. Testar Envio de Mensagem
```bash
# Servidor publica para Financeiro
POST /api/notifications
{
  "title": "Teste",
  "message": "Mensagem para Financeiro",
  "departments": ["Financeiro"]
}

# Agente deve receber em [DEPARTAMENTO]
[DEPARTAMENTO] Mensagem recebida: {...}
```

---

## 📝 Resumo das Mudanças

| Componente | Antes | Depois |
|-----------|-------|--------|
| **basicConsume()** | Usa admin (all permissions) | Usa agent-consumer (read-only) |
| **exchangeDeclare** | Ativo (tenta criar) | Passivo (apenas verifica) |
| **queueDeclare** | Ativo (tenta criar) | Passivo (apenas verifica) |
| **Agente Exchange** | Tenta declarar ❌ | Apenas verifica ✅ |
| **Agente Filas** | Tenta criar/bind ❌ | Cria se não existir ✅ |
| **Permissões** | Misturadas | Segregadas (admin vs agent) |

---

## ✅ Checklist de Validação

- [ ] Permissões de `agent-consumer` são read-only no RabbitMQ
- [ ] Servidor cria exchange na inicialização (admin-producer)
- [ ] Agente usa declarações PASSIVAS para exchange existente
- [ ] Agente cria suas próprias filas (não tenta modificar exchange)
- [ ] Agente faz bind com routing patterns corretos
- [ ] Servidor publica com routing keys corretos
- [ ] Mensagens chegam nos agentes corretos
- [ ] Filtro de severidade funciona

---

**Status:** ✅ PRONTO PARA TESTE
**Data:** 2025-11-27
