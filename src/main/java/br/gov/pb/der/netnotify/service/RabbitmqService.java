package br.gov.pb.der.netnotify.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import lombok.Getter;
import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Getter
@Setter
public class RabbitmqService {

    private static final Logger log = LoggerFactory.getLogger(RabbitmqService.class);
    private static final String BROADCAST_ROUTING_KEY = "broadcast.general";
    private static final String DEPARTMENT_ROUTING_PREFIX = "department.";
    private static final String AGENT_ROUTING_PREFIX = "agent.";
    private static final String AGENT_QUEUE_PREFIX = "queue_agent_";
    private static final int TEMP_QUEUE_EXPIRES_MS = 60_000;

    @Value("${spring.rabbitmq.host}")
    private String factoryHost;
    @Value("${spring.rabbitmq.username}")
    private String factoryUsername;
    @Value("${spring.rabbitmq.password}")
    private String factoryPassword;
    @Value("${spring.rabbitmq.virtual-host}")
    private String factoryVirtualHost;
    
    // Credenciais separadas: produtor (servidor) e consumidor (agentes)
    @Value("${spring.rabbitmq.admin-producer-username:admin-producer}")
    private String adminProducerUsername;
    @Value("${spring.rabbitmq.admin-producer-password:adminproducer123}")
    private String adminProducerPassword;
    
    @Value("${spring.rabbitmq.agent-consumer-username:agent-consumer}")
    private String agentConsumerUsername;
    @Value("${spring.rabbitmq.agent-consumer-password:agentconsumer123}")
    private String agentConsumerPassword;

    @Autowired
    private TopicExchange fanoutExchange;

    /**
     * Factory padrão (usa credenciais do application.yaml)
     */
    public ConnectionFactory rabbitConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(factoryHost);
        factory.setUsername(factoryUsername);
        factory.setPassword(factoryPassword);
        factory.setVirtualHost(factoryVirtualHost);
        configureConnectionFactory(factory);
        return factory;
    }

    /**
     * Factory para produtor (servidor publicando mensagens)
     * Usa credenciais admin-producer com permissão de write
     */
    public ConnectionFactory rabbitConnectionFactoryProducer() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(factoryHost);
        factory.setUsername(adminProducerUsername);
        factory.setPassword(adminProducerPassword);
        factory.setVirtualHost(factoryVirtualHost);
        configureConnectionFactory(factory);
        return factory;
    }

    /**
     * Factory para consumidor (agentes recebendo mensagens)
     * Usa credenciais agent-consumer com permissão read-only
     */
    public ConnectionFactory rabbitConnectionFactoryConsumer() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(factoryHost);
        factory.setUsername(agentConsumerUsername);
        factory.setPassword(agentConsumerPassword);
        factory.setVirtualHost(factoryVirtualHost);
        configureConnectionFactory(factory);
        return factory;
    }

    /**
     * Configuração comum para todas as factories
     */
    private void configureConnectionFactory(ConnectionFactory factory) {
        // Configurações de otimização
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(10000);
        factory.setRequestedHeartbeat(60);
        factory.setConnectionTimeout(30000);
    }

    public void initializeExchangeAndQueue() throws IOException, TimeoutException {
        ConnectionFactory factory = rabbitConnectionFactoryProducer();
        try (Connection connection = factory.newConnection();
                Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(fanoutExchange.getName(), fanoutExchange.getType(), fanoutExchange.isDurable());
            log.info("Initialized exchange: name={}, type={}, durable={}",
                    fanoutExchange.getName(), fanoutExchange.getType(), fanoutExchange.isDurable());
        }
    }

    public void basicPublish(String message) {
        publishWithRoutingKey(message, BROADCAST_ROUTING_KEY);
    }

    public String basicConsume() {
        return basicConsume(5000);
    }

    /**
     * Consome de forma síncrona a primeira mensagem disponível na fila.
     * Aguarda até timeoutMillis milissegundos antes de retornar null.
     */
    public String basicConsume(long timeoutMillis) {
        BlockingQueue<String> messageQueue = new ArrayBlockingQueue<>(1);

        try (Connection connection = rabbitConnectionFactoryConsumer().newConnection();
                Channel channel = connection.createChannel()) {

            // Apenas verifica passivamente se exchange e fila existem (sem declarar)
            // O exchange deve ter sido criado pelo servidor (admin-producer) na inicialização
            try {
                channel.exchangeDeclarePassive(fanoutExchange.getName());
            } catch (IOException e) {
                System.err.println("Warning: Exchange '" + fanoutExchange.getName() + "' does not exist. " +
                        "Make sure server has initialized it. " + e.getMessage());
                throw e;
            }
            
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("x-expires", TEMP_QUEUE_EXPIRES_MS);
            String queueName = channel.queueDeclare("", false, true, true, arguments).getQueue();
            channel.queueBind(queueName, fanoutExchange.getName(), BROADCAST_ROUTING_KEY);

            System.out.println(" [*] Waiting for messages for " + timeoutMillis + "ms");

            // Callback para receber mensagens
            String consumerTag = channel.basicConsume(queueName, true,
                    (tag, delivery) -> {
                        String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                        System.out.println(" [x] Received '" + msg + "'");
                        messageQueue.offer(msg);
                    },
                    tag -> System.out.println("Consumer cancelled: " + tag));

            // Aguarda por uma mensagem ou timeout
            String message = messageQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);

            // Cancela o consumer
            if (consumerTag != null) {
                channel.basicCancel(consumerTag);
            }

            return message;

        } catch (IOException | TimeoutException | InterruptedException e) {
            System.err.println("Error consuming message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Verifica se a conexão com RabbitMQ está funcionando
     */
    public boolean isConnectionHealthy() {
        try (Connection connection = rabbitConnectionFactory().newConnection()) {
            return connection.isOpen();
        } catch (Exception e) {
            System.err.println("RabbitMQ connection health check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lista todas as queues associadas ao canal atual (exchange)
     * Retorna uma lista de nomes de queues vinculadas ao exchange
     */
    public List<String> listQueuesForCurrentChannel() {
        List<String> queues = new ArrayList<>();

        try (Connection connection = rabbitConnectionFactoryProducer().newConnection();
                Channel channel = connection.createChannel()) {

            // Apenas verifica passivamente se o exchange existe
            try {
                channel.exchangeDeclarePassive(fanoutExchange.getName());
            } catch (IOException e) {
                System.err.println("Exchange does not exist: " + e.getMessage());
                return queues;
            }

            log.info("Exchange '{}' is available. Queue discovery should be done via the Management API.",
                    fanoutExchange.getName());

            return queues;

        } catch (IOException | TimeoutException e) {
            System.err.println("Error listing queues: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Obtém informações detalhadas sobre as queues do canal atual
     * Inclui contagem de mensagens e status de consumer
     */
    public String getQueueDetails(String queueName) {
        try (Connection connection = rabbitConnectionFactory().newConnection();
                Channel channel = connection.createChannel()) {

            com.rabbitmq.client.AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(queueName);

            return String.format("Queue: %s, Messages: %d, Consumers: %d",
                    queueName, declareOk.getMessageCount(), declareOk.getConsumerCount());

        } catch (IOException | TimeoutException e) {
            return "Error getting queue details: " + e.getMessage();
        }
    }

    /**
     * Publica mensagem usando um routing key específico (útil para Topic Exchange)
     * Permite enviar mensagens para departamentos específicos
     * Usa credenciais de produtor (admin-producer)
     * 
     * @param message          Conteúdo da mensagem
     * @param customRoutingKey Routing key customizado (ex: "department.financeiro",
     *                         "department.rh")
     */
    public void publishWithRoutingKey(String message, String customRoutingKey) {
        try (Connection connection = rabbitConnectionFactoryProducer().newConnection();
                Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(fanoutExchange.getName(), fanoutExchange.getType(), fanoutExchange.isDurable());

            channel.basicPublish(fanoutExchange.getName(), customRoutingKey, null,
                    message.getBytes(StandardCharsets.UTF_8));
            log.info("[RabbitMQ] Sent routingKey='{}'", customRoutingKey);

        } catch (IOException | TimeoutException e) {
            log.error("Error publishing message with routing key '{}': {}", customRoutingKey, e.getMessage());
        }
    }

    /**
     * Publica mensagem para um departamento específico
     * 
     * @param message        Conteúdo da mensagem
     * @param departmentName Nome do departamento
     */
    public void publishToDepartment(String message, String departmentName) {
        publishWithRoutingKey(message, buildDepartmentRoutingKey(departmentName));
    }

    /**
     * Publica mensagem para múltiplos departamentos
     * 
     * @param message       Conteúdo da mensagem
     * @param departmentIds Lista de IDs de departamentos
     */
    public void publishToDepartments(String message, List<String> departmentNames) {
        for (String deptName : departmentNames) {
            publishToDepartment(message, deptName);
        }
    }

    /**
     * Publica mensagem para todos os departamentos (wildcard)
     * Usa "department.#" para alcançar todos os padrões de departamento
     * 
     * @param message Conteúdo da mensagem
     */
    public void publishToAllDepartments(String message) {
        basicPublish(message);
    }

    /**
     * Publica mensagem para ABSOLUTAMENTE TODOS (departamentos + broadcast)
     * 
     * @param message Conteúdo da mensagem
     */
    public void publishToEveryone(String message) {
        basicPublish(message);
    }

    public void publishToAgent(String message, String agentHostname) {
        publishWithRoutingKey(message, buildAgentRoutingKey(agentHostname));
    }

    /**
     * Compatibilidade para chamadas legadas baseadas em nome de fila.
     *
     * <p>Quando a fila segue a convenção {@code queue_agent_<hostname>}, a mensagem é
     * redirecionada para a routing key {@code agent.<hostname>}. Se o nome não
     * seguir essa convenção, faz fallback para publicação direta na fila.
     *
     * @param targetQueueName nome exato da fila de destino
     * @param message         conteúdo (geralmente JSON)
     */
    public void publishDirectToQueue(String targetQueueName, String message) {
        String agentHostname = extractAgentHostnameFromQueueName(targetQueueName);
        if (agentHostname != null) {
            publishToAgent(message, agentHostname);
            return;
        }

        try (Connection connection = rabbitConnectionFactoryProducer().newConnection();
                Channel channel = connection.createChannel()) {

            // Garante que a fila existe (passive — não cria nem altera)
            channel.queueDeclarePassive(targetQueueName);

            // Exchange padrão ("") roteia pelo nome da fila como routing key
            channel.basicPublish("", targetQueueName, null,
                    message.getBytes(StandardCharsets.UTF_8));
            log.info("[RabbitMQ] Mensagem enviada diretamente para fila '{}': {}", targetQueueName, message);

        } catch (IOException | TimeoutException e) {
            log.error("[RabbitMQ] Erro ao publicar diretamente na fila '{}': {}", targetQueueName, e.getMessage());
        }
    }

    private String buildDepartmentRoutingKey(String departmentName) {
        return DEPARTMENT_ROUTING_PREFIX + normalizeRoutingSegment(departmentName);
    }

    private String buildAgentRoutingKey(String agentHostname) {
        return AGENT_ROUTING_PREFIX + normalizeRoutingSegment(agentHostname);
    }

    private String normalizeRoutingSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Routing segment cannot be blank");
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String extractAgentHostnameFromQueueName(String queueName) {
        if (queueName == null || queueName.isBlank()) {
            return null;
        }
        if (queueName.startsWith(AGENT_QUEUE_PREFIX)) {
            return queueName.substring(AGENT_QUEUE_PREFIX.length());
        }
        return null;
    }

    /**
     * Consome mensagens de uma fila de departamento específico
     * 
     * @param queueName     Nome da fila do departamento
     * @param timeoutMillis Tempo máximo de espera
     * @return Mensagem recebida ou null se timeout
     */
    public String consumeFromDepartmentQueue(String queueName, long timeoutMillis) {
        BlockingQueue<String> messageQueue = new ArrayBlockingQueue<>(1);

        try (Connection connection = rabbitConnectionFactoryConsumer().newConnection();
                Channel channel = connection.createChannel()) {

            // Apenas verifica passivamente se a fila existe (sem tentar declarar)
            try {
                channel.queueDeclarePassive(queueName);
            } catch (IOException e) {
                System.err.println("Queue does not exist: " + queueName + ". " + e.getMessage());
                return null;
            }

            String consumerTag = channel.basicConsume(queueName, true,
                    (tag, delivery) -> {
                        String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                        System.out.println(" [x] Received from department queue '" + queueName + "': '" + msg + "'");
                        messageQueue.offer(msg);
                    },
                    tag -> System.out.println("Consumer cancelled: " + tag));

            String message = messageQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);

            if (consumerTag != null) {
                channel.basicCancel(consumerTag);
            }

            return message;

        } catch (IOException | TimeoutException | InterruptedException e) {
            System.err.println("Error consuming from department queue: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

}
