/**
 * CONFIGURAÇÃO DO AGENTE PARA RECEBER MENSAGENS GERAIS E DE DEPARTAMENTO
 * 
 * O agente deve ser configurado para:
 * 1. Receber mensagens GERAIS (broadcast para todos)
 * 2. Receber mensagens específicas do DEPARTAMENTO onde está instalado
 * 
 * SOLUÇÃO: Usar Topic Exchange com 2 filas por agente
 */

// ============================================================================
// ARQUIVO: settings.properties (no agente)
// ============================================================================

rabbitmq.host=localhost
rabbitmq.port=5672
rabbitmq.username=admin
rabbitmq.password=admin
rabbitmq.virtualhost=/

# Exchange type: deve ser "topic" (não "fanout")
rabbitmq.exchange=netnotify.topic

# Identificação do agente (obtida automaticamente no startup)
# Formato: department.ID.NOME ou pode ser configurado manualmente
agent.department.id=1
agent.department.name=Financeiro
agent.hostname=maquina-001

# Filas serão criadas automaticamente com base no hostname
# Fila Geral: netnotify.general.{hostname}
# Fila Dept: netnotify.department.{id}.{hostname}


// ============================================================================
// CÓDIGO ADAPTADO DO RabbitmqService para o AGENTE
// ============================================================================

package br.gov.pb.der.netnotifyagent.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
    
    // Novos campos para departamento
    private Integer departmentId;
    private String departmentName;
    private String hostname;
    
    // Filas específicas
    private String generalQueueName;  // Fila para mensagens gerais
    private String departmentQueueName;  // Fila para mensagens do departamento
    
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
            
            // Carrega informações do departamento
            this.departmentId = Integer.parseInt(settings.getProperty("agent.department.id", "0"));
            this.departmentName = settings.getProperty("agent.department.name", "Unknown");
            this.hostname = settings.getProperty("agent.hostname", getLocalHostname());
            
        } catch (IOException e) {
            System.err.println("Erro ao carregar configurações: " + e.getMessage());
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

    private DeliverCallback createDeliverCallback() {
        return (consumerTag, delivery) -> {
            try {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[" + consumerTag + "] Mensagem recebida: " + message);

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
            System.out.println("[RabbitmqService] Aviso: não foi possível extrair nível da mensagem: " + e.getMessage());
        }
        return null;
    }

    private CancelCallback createCancelCallback(String queueType) {
        return consumerTag -> {
            System.out.println("Consumidor " + queueType + " cancelado: " + consumerTag);
        };
    }

    /**
     * Configura o Topic Exchange e cria 2 filas:
     * 1. Fila Geral: recebe TODAS as mensagens (routing: "broadcast.#")
     * 2. Fila Departamento: recebe apenas mensagens do departamento (routing: "department.{id}.*")
     */
    private void setupQueueAndExchangeConsumer(Channel channel) throws IOException {
        
        // ========== SETUP DO EXCHANGE ==========
        // Declara como Topic Exchange (permite padrões com # e *)
        String exchangeType = "topic";
        channel.exchangeDeclare(exchangeName, exchangeType, true);
        System.out.println("Exchange declarado/verificado: " + exchangeName + " (tipo: " + exchangeType + ")");

        // ========== FILA 1: MENSAGENS GERAIS ==========
        this.generalQueueName = "netnotify.general." + hostname;
        
        // Fila durável (persiste mesmo com agente desconectado)
        channel.queueDeclare(generalQueueName, true, false, false, null);
        System.out.println("Fila Geral criada/verificada: " + generalQueueName);

        // Bind com routing key para BROADCAST (mensagens enviadas para todos)
        // Aceita: broadcast.* (qualquer mensagem que comece com broadcast)
        channel.queueBind(generalQueueName, exchangeName, "broadcast.*");
        System.out.println("Fila Geral vinculada com padrão: broadcast.*");

        // Inicia consumo da fila geral
        channel.basicConsume(generalQueueName, true, 
                createDeliverCallback(), 
                createCancelCallback("GERAL"));

        // ========== FILA 2: MENSAGENS DO DEPARTAMENTO ==========
        if (departmentId != null && departmentId > 0) {
            this.departmentQueueName = "netnotify.department." + departmentId + "." + hostname;
            
            // Fila durável para o departamento
            channel.queueDeclare(departmentQueueName, true, false, false, null);
            System.out.println("Fila Departamento criada/verificada: " + departmentQueueName);

            // Bind com routing key específico do departamento
            // Padrão: department.{id}.* (mensagens específicas deste departamento)
            String deptRoutingPattern = "department." + departmentId + ".*";
            channel.queueBind(departmentQueueName, exchangeName, deptRoutingPattern);
            System.out.println("Fila Departamento vinculada com padrão: " + deptRoutingPattern);

            // Inicia consumo da fila de departamento
            channel.basicConsume(departmentQueueName, true,
                    createDeliverCallback(),
                    createCancelCallback("DEPARTAMENTO"));
        } else {
            System.out.println("AVISO: departmentId não configurado. Agente receberá apenas mensagens gerais.");
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
                System.out.println(status + "...");

                try (Connection connection = factory.newConnection();
                     Channel channel = connection.createChannel()) {

                    setupQueueAndExchangeConsumer(channel);
                    status = "Connected (General: " + generalQueueName + 
                             ", Dept: " + (departmentQueueName != null ? departmentQueueName : "N/A") + ")";
                    System.out.println("✓ Conectado! Aguardando mensagens...");

                    waitForConnection();

                } catch (ShutdownSignalException e) {
                    lastError = "Shutdown: " + e.getMessage();
                    status = "Disconnected";
                    System.err.println("Conexão fechada pelo servidor: " + e.getMessage());
                } catch (IOException e) {
                    lastError = "IO: " + e.getMessage();
                    status = "Disconnected";
                    System.err.println("Erro de I/O na conexão: " + e.getMessage());
                } catch (TimeoutException e) {
                    lastError = "Timeout: " + e.getMessage();
                    status = "Disconnected";
                    System.err.println("Timeout na conexão: " + e.getMessage());
                }

            } catch (InterruptedException e) {
                lastError = e.getMessage();
                status = "Disconnected";
                System.err.println("Erro geral na conexão com RabbitMQ: " + e.getMessage());
            }

            if (!shouldStop) {
                System.out.println("Tentando reconectar em " + (RECONNECT_DELAY_MS / 1000) + " segundos...");
                Object reconnectMonitor = new Object();
                synchronized (reconnectMonitor) {
                    try {
                        reconnectMonitor.wait(RECONNECT_DELAY_MS);
                    } catch (InterruptedException e) {
                        System.out.println("Aplicação interrompida.");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        status = "Stopped";
        System.out.println("Serviço RabbitMQ finalizado.");
    }

    public void stop() {
        this.shouldStop = true;
        System.out.println("Parando o serviço RabbitMQ...");
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
        sb.append("=== CONFIGURAÇÃO DO AGENTE ===\n");
        sb.append("Host RabbitMQ: ").append(host).append(":").append(port).append('\n');
        sb.append("Exchange: ").append(exchangeName).append(" (Topic)\n");
        sb.append("Departamento: ").append(departmentId).append(" - ").append(departmentName).append('\n');
        sb.append("Hostname: ").append(hostname).append('\n');
        sb.append("\n=== FILAS ===\n");
        sb.append("Fila Geral: ").append(generalQueueName).append('\n');
        sb.append("  Routing: broadcast.*\n");
        if (departmentQueueName != null) {
            sb.append("Fila Departamento: ").append(departmentQueueName).append('\n');
            sb.append("  Routing: department.").append(departmentId).append(".*\n");
        }
        sb.append("\n=== STATUS ===\n");
        sb.append("Status: ").append(status).append('\n');
        if (lastError != null && !lastError.isEmpty()) {
            sb.append("Último erro: ").append(lastError).append('\n');
        }
        return sb.toString();
    }

    // Getters de compatibilidade
    public String getHost() { return host; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getExchangeName() { return exchangeName; }
    public String getVirtualHost() { return virtualHost; }
    public int getPort() { return port; }
    public Integer getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }
    public String getHostname() { return hostname; }
    public String getGeneralQueueName() { return generalQueueName; }
    public String getDepartmentQueueName() { return departmentQueueName; }
}


// ============================================================================
// COMO USAR NO SERVIDOR PARA ENVIAR MENSAGENS
// ============================================================================

// 1. MENSAGENS GERAIS (para todos os agentes):
rabbitmqService.publishWithRoutingKey(message, "broadcast.general");

// 2. MENSAGENS PARA UM DEPARTAMENTO ESPECÍFICO:
rabbitmqService.publishToDepartment(message, 1, "Financeiro");
// Routing key: "department.1.financeiro"

// 3. MENSAGENS PARA MÚLTIPLOS DEPARTAMENTOS:
rabbitmqService.publishToDepartments(message, Arrays.asList(1, 2, 3));

// 4. MENSAGENS PARA TODOS OS DEPARTAMENTOS:
rabbitmqService.publishToAllDepartments(message);
// Routing key: "department.#"


// ============================================================================
// FLUXO DE ROTEAMENTO
// ============================================================================

/*
SERVIDOR envia mensagem com routing key:
  └─ "broadcast.general"
       └─ Exchange "netnotify.topic" roteia para:
            └─ Fila "netnotify.general.maquina-001" ✓
            └─ Fila "netnotify.general.maquina-002" ✓
            └─ Fila "netnotify.general.maquina-003" ✓
            └─ (ignora filas de departamento)

SERVIDOR envia mensagem com routing key:
  └─ "department.1.financeiro"
       └─ Exchange "netnotify.topic" roteia para:
            └─ Fila "netnotify.department.1.maquina-001" ✓ (se dept=1)
            └─ Fila "netnotify.department.1.maquina-002" ✓ (se dept=1)
            └─ (ignora agentes de outros departamentos)
            └─ (ignora fila geral? NÃO, mensagens gerais ainda vêm por broadcast.*)

*/
