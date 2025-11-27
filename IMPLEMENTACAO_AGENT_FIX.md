# Implementação da Correção - Agent RabbitmqService

## 📋 Resumo da Mudança

O agente tentava **declarar** o exchange, o que causa erro `ACCESS_REFUSED` porque `agent-consumer` tem permissão apenas de **READ**.

### Mudança Principal

**Exchange Declaration:**
- ❌ ANTES: `channel.exchangeDeclare(exchangeName, "topic", false);`
- ✅ DEPOIS: `channel.exchangeDeclarePassive(exchangeName);`

---

## 🔧 Instruções de Implementação

### Passo 1: Abrir o arquivo do agente

```
NetNotifyAgent/
  src/
    main/
      java/
        br/gov/pb/der/netnotifyagent/service/
          RabbitmqService.java  ← ABRIR ESTE ARQUIVO
```

### Passo 2: Localizar o método `setupQueueAndExchangeConsumer`

Procure pela linha:
```java
private void setupQueueAndExchangeConsumer(Channel channel) throws IOException {
```

### Passo 3: Substituir a declaração do Exchange

**Encontrar:**
```java
// ========== SETUP DO EXCHANGE ==========
// durable=false para matching com o exchange existente no servidor
channel.exchangeDeclare(exchangeName, "topic", false);
System.out.println("✓ Exchange declarado: " + exchangeName + " (tipo: topic, não-durável)");
```

**Substituir por:**
```java
// ========== VERIFICAÇÃO PASSIVA DO EXCHANGE ==========
// Apenas verifica se o exchange existe (não cria)
try {
    channel.exchangeDeclarePassive(exchangeName);
    System.out.println("✓ Exchange existe: " + exchangeName + " (tipo: topic)");
} catch (IOException e) {
    System.err.println("✗ ERRO: Exchange '" + exchangeName + "' não existe!");
    System.err.println("  O servidor deve ter criado este exchange durante a inicialização.");
    System.err.println("  Verifique se o servidor está rodando.");
    throw e;
}
```

### Passo 4: Melhorar o tratamento de filas (OPCIONAL)

Para ficar consistente com as melhores práticas:

**Para FILA GERAL:**
```java
// ========== FILA 1: MENSAGENS GERAIS (broadcast) ==========
this.generalQueueName = "queue_general_" + hostname;

try {
    channel.queueDeclarePassive(generalQueueName);
    System.out.println("✓ Fila Geral já existe: " + generalQueueName);
} catch (IOException e) {
    System.err.println("✗ ERRO: Fila '" + generalQueueName + "' não existe!");
    System.err.println("  Criando fila...");
    channel.queueDeclare(generalQueueName, true, false, false, null);
    System.out.println("✓ Fila Geral criada: " + generalQueueName);
}
```

**Para FILA DEPARTAMENTO:**
```java
if (departmentName != null && !departmentName.isEmpty() && !departmentName.equals("unknown")) {
    String deptNameFormatted = departmentName.toLowerCase().replace(" ", "_");
    this.departmentQueueName = "queue_department_" + deptNameFormatted + "_" + hostname;
    
    try {
        channel.queueDeclarePassive(departmentQueueName);
        System.out.println("✓ Fila Departamento já existe: " + departmentQueueName);
    } catch (IOException e) {
        System.err.println("✗ ERRO: Fila '" + departmentQueueName + "' não existe!");
        System.err.println("  Criando fila...");
        channel.queueDeclare(departmentQueueName, true, false, false, null);
        System.out.println("✓ Fila Departamento criada: " + departmentQueueName);
    }
    // ... resto do código ...
}
```

### Passo 5: Melhorar mensagem de erro (OPCIONAL)

Localizar o catch em `startConsuming()`:
```java
} catch (IOException e) {
    // ... código existente ...
    
    // Adicionar detecção de ACCESS_REFUSED:
    if (e.getMessage() != null && e.getMessage().contains("ACCESS-REFUSED")) {
        System.err.println("  → SOLUÇÃO: Verifique se o usuário '" + username + "' tem permissão READ");
        System.err.println("            Usuário deve ser 'agent-consumer' com permissões read-only");
    }
}
```

---

## ✅ Validação Após Implementação

### 1. Recompilar o Agente
```bash
cd NetNotifyAgent
mvn clean compile
# ou
gradlew build  (se usar Gradle)
```

### 2. Verificar Arquivo Completo

O arquivo deve ter uma estrutura assim:

```java
private void setupQueueAndExchangeConsumer(Channel channel) throws IOException {
    
    System.out.println("\n[SETUP] Configurando filas de consumo...");
    
    // ========== VERIFICAÇÃO PASSIVA DO EXCHANGE ==========
    try {
        channel.exchangeDeclarePassive(exchangeName);
        System.out.println("✓ Exchange existe: " + exchangeName + " (tipo: topic)");
    } catch (IOException e) {
        System.err.println("✗ ERRO: Exchange '" + exchangeName + "' não existe!");
        throw e;
    }

    // ========== FILA 1: MENSAGENS GERAIS (broadcast) ==========
    this.generalQueueName = "queue_general_" + hostname;
    System.out.println("\n[FILA 1] Configurando fila geral...");
    
    // ... resto ...
    
    channel.queueBind(generalQueueName, exchangeName, "broadcast.*");
    // ... resto ...
    
    // ========== FILA 2: MENSAGENS DO DEPARTAMENTO ==========
    System.out.println("\n[FILA 2] Configurando fila de departamento...");
    
    if (departmentName != null && !departmentName.isEmpty() && !departmentName.equals("unknown")) {
        // ... resto ...
    }
}
```

### 3. Iniciar o Agente

Logs esperados:

```
✓ Configuração carregada:
  - Host: localhost:5672
  - Usuário: agent-consumer (agent-consumer, read-only)
  - Exchange: netnotify_topic
  - Departamento: Financeiro
  - Hostname: desktop-01

[DEBUG] ConnectionFactory configurada (agent-consumer):
  - Host: localhost
  - Port: 5672
  - VirtualHost: /
  - Username: agent-consumer
  - Exchange: netnotify_topic
  - Permissões: READ-ONLY (não pode declarar/modificar)

→ Connecting to localhost:5672...

[SETUP] Configurando filas de consumo...

✓ Exchange existe: netnotify_topic (tipo: topic)

[FILA 1] Configurando fila geral...
✓ Fila Geral criada: queue_general_desktop-01
  → Binding Pattern: broadcast.*
  → Receberá: mensagens gerais para TODOS os agentes
  → Consumindo: SIM

[FILA 2] Configurando fila de departamento...
✓ Fila Departamento criada: queue_department_financeiro_desktop-01
  → Binding Pattern: department.financeiro
  → Receberá: mensagens específicas de Financeiro
  → Consumindo: SIM

✓ Conectado! Aguardando mensagens...
```

### 4. Testar Recebimento de Mensagem

Publicar uma mensagem do servidor:
```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Teste",
    "message": "Mensagem de teste para Financeiro",
    "departments": ["Financeiro"]
  }'
```

Agente deve receber:
```
[DEPARTAMENTO] Mensagem recebida: {...}
✓ Conectado! Aguardando mensagens...
```

---

## 🔐 Verificação de Permissões

Se ainda receber erro ACCESS_REFUSED, verificar permissões no RabbitMQ:

```bash
# Listar usuários e permissões
docker exec netnotify-rabbitmq rabbitmqctl list_permissions

# Saída esperada:
# Listing permissions for vhost "/" ...
# user              configure  write  read
# admin-producer    .*         .*     
# agent-consumer                      .*
# admin             .*         .*     .*

# Se agent-consumer não aparecer, criar:
docker exec netnotify-rabbitmq rabbitmqctl add_user agent-consumer agentconsumer123
docker exec netnotify-rabbitmq rabbitmqctl set_permissions -p / agent-consumer "" "" ".*"
```

---

## 📝 Arquivo Completo Disponível

Um arquivo com o código completo validado está disponível:
```
AGENT_RABBITMQ_SERVICE.java
```

Copiar e colar no seu projeto se preferir ter a implementação completa.

---

## 🎯 Resumo

| Antes | Depois |
|-------|--------|
| ❌ exchangeDeclare() ativo | ✅ exchangeDeclarePassive() |
| ❌ ACCESS_REFUSED | ✅ Funciona normalmente |
| ❌ Sem validação | ✅ Mensagens de erro claras |
| ❌ Logs confusos | ✅ Logs estruturados e informativos |

---

**Status:** ✅ PRONTO PARA IMPLEMENTAR
**Tempo Estimado:** 5-10 minutos
**Risco:** BAIXO (apenas muda tipo de declaração)
