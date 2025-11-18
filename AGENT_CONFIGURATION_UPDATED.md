/**
 * ============================================================================
 * CONFIGURAÇÃO DO AGENTE NETNOTIFY - VERSÃO ATUALIZADA
 * ============================================================================
 * 
 * O agente será instalado em todas as máquinas da empresa e receberá:
 * 1. Mensagens GERAIS (broadcast para todos)
 * 2. Mensagens específicas do DEPARTAMENTO onde está instalado
 * 
 * Usa Topic Exchange com roteamento baseado em NOMES DE DEPARTAMENTO
 */

// ============================================================================
// 1. ARQUIVO DE CONFIGURAÇÃO DO AGENTE: settings.properties
// ============================================================================

rabbitmq.host=localhost
rabbitmq.port=5672
rabbitmq.username=admin
rabbitmq.password=admin
rabbitmq.virtualhost=/

# Exchange: DEVE SER "topic" (não fanout)
rabbitmq.exchange=netnotify.topic

# CONFIGURAÇÃO DO DEPARTAMENTO (obtida automaticamente ou definida manualmente)
# Use apenas o NOME do departamento em minúsculas
agent.department.name=financeiro
# Ou para outro departamento:
# agent.department.name=recursos_humanos
# agent.department.name=operacoes

# Identificação da máquina (obtida automaticamente)
agent.hostname=maquina-001


// ============================================================================
// 2. ROUTING KEYS UTILIZADAS
// ============================================================================

/*
PADRÃO DE ROUTING KEYS:

1. MENSAGENS GERAIS:
   Routing Key: "broadcast.general"
   Alcança: TODAS as máquinas (todos os agentes)
   Exemplo: Avisos de manutenção, atualizações sistema

2. MENSAGENS PARA UM DEPARTAMENTO:
   Routing Key: "department.{nome_dept}"
   Exemplo: "department.financeiro"
   Alcança: Apenas máquinas do departamento Financeiro
   
3. MENSAGENS PARA MÚLTIPLOS DEPARTAMENTOS:
   Envia múltiplas mensagens com routing keys específicas
   
4. MENSAGENS PARA TODOS OS DEPARTAMENTOS:
   Routing Key: "department.#"
   Alcança: TODOS os departamentos (mas não broadcast)
   
5. MENSAGENS PARA ABSOLUTAMENTE TODOS:
   Envia com "broadcast.#" E "department.#"
   Alcança: TUDO
*/


// ============================================================================
// 3. CONFIGURAÇÃO DO SERVIDOR (NetnotifyApplication)
// ============================================================================

/*
No servidor, o RabbitmqService já está configurado com estes métodos:

1. publishWithRoutingKey(String message, String customRoutingKey)
   - Base para enviar com routing key customizada
   - Exemplo: publishWithRoutingKey(msg, "department.financeiro")

2. publishToDepartment(String message, String departmentName)
   - Envia para UM departamento específico
   - Exemplo: publishToDepartment(msg, "Financeiro")
   - Gera: "department.financeiro" (automático)

3. publishToDepartments(String message, List<String> departmentNames)
   - Envia para MÚLTIPLOS departamentos
   - Exemplo: publishToDepartments(msg, Arrays.asList("Financeiro", "RH", "Operações"))

4. publishToAllDepartments(String message)
   - Envia para TODOS os departamentos
   - Routing: "department.#"

5. publishToEveryone(String message)
   - Envia para ABSOLUTAMENTE TODOS
   - Usa "broadcast.#" + "department.#"

6. createDepartmentQueue(String departmentName)
   - Cria uma fila permanente para um departamento
   - Útil para setup inicial
*/


// ============================================================================
// 4. CONFIGURAÇÃO DO AGENTE: RabbitmqService (ATUALIZADO)
// ============================================================================

package br.gov.pb.der.netnotifyagent.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.ShutdownSignalException;

import br.gov.pb.der.netnotifyagent.ui.Alert;
import br.gov.pb.der.netnotifyagent.utils.Functions;
import br.gov.pb.der.netnotifyagent.utils.FilterSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RabbitmqService {

    private static final String RESOURCES_PATH = "resources/";
    private static final String SETTINGS_FILE = RESOURCES_PATH + "settings.properties";
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 60000;

    private String host;
    private String username;
    private String password;
    private String exchangeName;
    private String virtualHost;
    private int port;
    
    // NOVO: Informações do departamento (apenas NOME)
    private String departmentName;
    private String hostname;
    
    // Nomes das filas criadas
    private String generalQueueName;
    private String departmentQueueName;
    
    private Properties settings;
    private volatile boolean shouldStop = false;
    private volatile String status = "Stopped";
    private volatile String lastError = "";

    public RabbitmqService() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        try {
            this.settings = Functions.loadProperties(SETTINGS_FILE);
            this.host = settings.getProperty("rabbitmq.host");
            this.username = settings.getProperty("rabbitmq.username");
            this.password = settings.getProperty("rabbitmq.password");
            this.exchangeName = settings.getProperty("rabbitmq.exchange", "netnotify.topic");
            this.virtualHost = settings.getProperty("rabbitmq.virtualhost", "/");
            this.port = Integer.parseInt(settings.getProperty("rabbitmq.port", "5672"));
            
            // NOVO: Carrega APENAS o NOME do departamento
            this.departmentName = settings.getProperty("agent.department.name", "unknown");
            this.hostname = settings.getProperty("agent.hostname", getLocalHostname());
            
            System.out.println("✓ Configuração carregada:");
            System.out.println("  - Host: " + host + ":" + port);
            System.out.println("  - Departamento: " + departmentName);
            System.out.println("  - Hostname: " + hostname);
            
        } catch (IOException e) {
            System.err.println("✗ Erro ao carregar configurações: " + e.getMessage());
        }
    }

    private String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostName().replaceAll("[^a-zA-Z0-9\\-]", "_");
        } catch (UnknownHostException e) {
            return "agent-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private ConnectionFactory createConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(this.host);
        factory.setUsername(this.username);
        factory.setPassword(this.password);
        factory.setVirtualHost(this.virtualHost);
        factory.setPort(this.port);
        
        factory.setRequestedHeartbeat(HEARTBEAT_INTERVAL_MS / 1000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(RECONNECT_DELAY_MS);
        return factory;
    }

    private DeliverCallback createDeliverCallback(String queueType) {
        return (consumerTag, delivery) -> {
            try {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[" + queueType + "] Mensagem recebida: " + message);

                // Extrair nível da mensagem para verificar filtro
                String level = extractLevelFromMessage(message);

                // Verificar se a mensagem deve ser exibida baseado no filtro
                if (!FilterSettings.shouldShowMessage(level)) {
                    System.out.println("[RabbitmqService] Mensagem com nível '" + level + "' filtrada pelo usuário");
                    return;
                }

                Alert.getInstance().showHtml(message);
            } catch (Exception e) {
                System.err.println("Erro ao processar mensagem: " + e.getMessage());
            }
        };
    }

    private String extractLevelFromMessage(String message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(message);
            if (jsonNode.has("level")) {
                return jsonNode.get("level").asText();
            }
        } catch (Exception e) {
            System.out.println("[RabbitmqService] Aviso: não foi possível extrair nível: " + e.getMessage());
        }
        return null;
    }

    private CancelCallback createCancelCallback(String queueType) {
        return consumerTag -> {
            System.out.println("[" + queueType + "] Consumidor cancelado: " + consumerTag);
        };
    }

    /**
     * Configura 2 filas:
     * 1. Fila Geral: recebe mensagens broadcast para TODOS
     * 2. Fila Departamento: recebe mensagens específicas do departamento
     */
    private void setupQueueAndExchangeConsumer(Channel channel) throws IOException {
        
        // ========== SETUP DO EXCHANGE ==========
        channel.exchangeDeclare(exchangeName, "topic", true);
        System.out.println("✓ Exchange declarado: " + exchangeName + " (tipo: topic)");

        // ========== FILA 1: MENSAGENS GERAIS ==========
        this.generalQueueName = "queue_general_" + hostname;
        
        channel.queueDeclare(generalQueueName, true, false, false, null);
        System.out.println("✓ Fila Geral criada: " + generalQueueName);

        // Bind para receber BROADCAST de TODOS
        channel.queueBind(generalQueueName, exchangeName, "broadcast.*");
        System.out.println("  → Padrão: broadcast.*");

        // Inicia consumo da fila geral
        channel.basicConsume(generalQueueName, true, 
                createDeliverCallback("GERAL"), 
                createCancelCallback("GERAL"));

        // ========== FILA 2: MENSAGENS DO DEPARTAMENTO ==========
        if (departmentName != null && !departmentName.isEmpty() && !departmentName.equals("unknown")) {
            String deptNameFormatted = departmentName.toLowerCase().replace(" ", "_");
            this.departmentQueueName = "queue_department_" + deptNameFormatted + "_" + hostname;
            
            channel.queueDeclare(departmentQueueName, true, false, false, null);
            System.out.println("✓ Fila Departamento criada: " + departmentQueueName);

            // Bind para receber mensagens ESPECÍFICAS do departamento
            String deptRoutingPattern = "department." + deptNameFormatted;
            channel.queueBind(departmentQueueName, exchangeName, deptRoutingPattern);
            System.out.println("  → Padrão: " + deptRoutingPattern);

            // Inicia consumo da fila de departamento
            channel.basicConsume(departmentQueueName, true,
                    createDeliverCallback("DEPARTAMENTO"),
                    createCancelCallback("DEPARTAMENTO"));
        } else {
            System.out.println("⚠ AVISO: departmentName não configurado!");
            System.out.println("  Agente receberá APENAS mensagens gerais (broadcast)");
        }
    }

    private void waitForConnection() throws InterruptedException {
        Object monitor = new Object();
        synchronized (monitor) {
            while (!shouldStop) {
                monitor.wait(1000);
            }
        }
    }

    public void startConsuming() {
        ConnectionFactory factory = createConnectionFactory();

        while (!shouldStop) {
            try {
                status = "Connecting to " + host + ":" + port;
                System.out.println("→ " + status + "...");

                try (Connection connection = factory.newConnection();
                     Channel channel = connection.createChannel()) {

                    setupQueueAndExchangeConsumer(channel);
                    status = "Connected (General: " + generalQueueName + 
                             (departmentQueueName != null ? ", Dept: " + departmentQueueName : "") + ")";
                    System.out.println("✓ Conectado! Aguardando mensagens...\n");

                    waitForConnection();

                } catch (ShutdownSignalException e) {
                    lastError = "Shutdown: " + e.getMessage();
                    status = "Disconnected";
                    System.err.println("✗ Conexão fechada: " + e.getMessage());
                } catch (IOException e) {
                    lastError = "IO: " + e.getMessage();
                    status = "Disconnected";
                    System.err.println("✗ Erro de I/O: " + e.getMessage());
                } catch (TimeoutException e) {
                    lastError = "Timeout: " + e.getMessage();
                    status = "Disconnected";
                    System.err.println("✗ Timeout: " + e.getMessage());
                }

            } catch (InterruptedException e) {
                lastError = e.getMessage();
                status = "Disconnected";
                System.err.println("✗ Erro na conexão: " + e.getMessage());
            }

            if (!shouldStop) {
                System.out.println("→ Tentando reconectar em " + (RECONNECT_DELAY_MS / 1000) + "s...");
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException e) {
                    System.out.println("Aplicação interrompida.");
                    break;
                }
            }
        }

        status = "Stopped";
        System.out.println("✓ Serviço RabbitMQ finalizado.");
    }

    public void stop() {
        this.shouldStop = true;
        System.out.println("→ Parando o serviço RabbitMQ...");
    }

    // ========== GETTERS ==========
    public String getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("     AGENTE NETNOTIFY - RESUMO DE CONFIGURAÇÃO\n");
        sb.append("═══════════════════════════════════════════════════════\n\n");
        
        sb.append("🔧 CONEXÃO RABBITMQ\n");
        sb.append("  Host: ").append(host).append(":").append(port).append('\n');
        sb.append("  Exchange: ").append(exchangeName).append(" (Topic)\n");
        sb.append("  VirtualHost: ").append(virtualHost).append('\n\n');
        
        sb.append("🏢 INFORMAÇÕES DO AGENTE\n");
        sb.append("  Departamento: ").append(departmentName).append('\n');
        sb.append("  Hostname: ").append(hostname).append('\n\n');
        
        sb.append("📨 FILAS ATIVAS\n");
        sb.append("  [GERAL] ").append(generalQueueName).append('\n');
        sb.append("    └─ Routing: broadcast.*\n");
        if (departmentQueueName != null) {
            String deptFormatted = departmentName.toLowerCase().replace(" ", "_");
            sb.append("  [DEPARTAMENTO] ").append(departmentQueueName).append('\n');
            sb.append("    └─ Routing: department.").append(deptFormatted).append('\n');
        }
        sb.append('\n');
        
        sb.append("📊 STATUS ATUAL\n");
        sb.append("  Status: ").append(status).append('\n');
        if (lastError != null && !lastError.isEmpty()) {
            sb.append("  Último erro: ").append(lastError).append('\n');
        }
        sb.append("═══════════════════════════════════════════════════════\n");
        
        return sb.toString();
    }

    // Getters
    public String getHost() { return host; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getExchangeName() { return exchangeName; }
    public String getVirtualHost() { return virtualHost; }
    public int getPort() { return port; }
    public String getDepartmentName() { return departmentName; }
    public String getHostname() { return hostname; }
    public String getGeneralQueueName() { return generalQueueName; }
    public String getDepartmentQueueName() { return departmentQueueName; }
}


// ============================================================================
// 5. EXEMPLO DE USO NO SERVIDOR
// ============================================================================

@RestController
@RequestMapping("/messages")
public class MessageController {
    
    @Autowired
    private RabbitmqService rabbitmqService;
    
    // Enviar mensagem geral (todos recebem)
    @PostMapping("/send-broadcast")
    public ResponseEntity<?> sendBroadcast(@RequestBody String message) {
        rabbitmqService.publishWithRoutingKey(message, "broadcast.general");
        return ResponseEntity.ok("Mensagem enviada para TODOS");
    }
    
    // Enviar para um departamento
    @PostMapping("/send-to-department")
    public ResponseEntity<?> sendToDepartment(@RequestParam String deptName, @RequestBody String message) {
        rabbitmqService.publishToDepartment(message, deptName);
        return ResponseEntity.ok("Mensagem enviada para: " + deptName);
    }
    
    // Enviar para múltiplos departamentos
    @PostMapping("/send-to-departments")
    public ResponseEntity<?> sendToMultiple(
        @RequestParam List<String> departments, 
        @RequestBody String message) {
        rabbitmqService.publishToDepartments(message, departments);
        return ResponseEntity.ok("Mensagem enviada para: " + departments);
    }
    
    // Enviar para TODOS
    @PostMapping("/send-to-everyone")
    public ResponseEntity<?> sendToEveryone(@RequestBody String message) {
        rabbitmqService.publishToEveryone(message);
        return ResponseEntity.ok("Mensagem enviada para TODOS");
    }
}


// ============================================================================
// 6. TABELA DE ROTEAMENTO FINAL
// ============================================================================

/*
┌─────────────────────────────────────────────────────────────────────────┐
│ ROUTING KEY                    │ ALCANÇA                               │
├────────────────────────────────┼──────────────────────────────────────┤
│ broadcast.general              │ ✓ TODOS os agentes                   │
│ broadcast.*                    │ ✓ TODOS os agentes (padrão)          │
├────────────────────────────────┼──────────────────────────────────────┤
│ department.financeiro          │ ✓ Apenas Financeiro                  │
│ department.recursos_humanos    │ ✓ Apenas RH                          │
│ department.operacoes           │ ✓ Apenas Operações                   │
├────────────────────────────────┼──────────────────────────────────────┤
│ department.#                   │ ✓ TODOS os departamentos             │
│ broadcast.# + department.#     │ ✓ ABSOLUTAMENTE TUDO                 │
└─────────────────────────────────────────────────────────────────────────┘
*/
