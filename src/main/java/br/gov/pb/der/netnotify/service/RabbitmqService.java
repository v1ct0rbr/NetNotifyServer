package br.gov.pb.der.netnotify.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import lombok.Getter;
import lombok.Setter;

@Service
@Getter
@Setter
public class RabbitmqService {

    @Value("${spring.rabbitmq.host}")
    private String factoryHost;
    @Value("${spring.rabbitmq.username}")
    private String factoryUsername;
    @Value("${spring.rabbitmq.password}")
    private String factoryPassword;
    @Value("${spring.rabbitmq.virtual-host}")
    private String factoryVirtualHost;
    @Value("${spring.rabbitmq.queue:notification_queue}")
    private String queueName;
    @Value("${spring.rabbitmq.routing-key:notification_key}")
    private String routingKey;

    @Autowired
    private FanoutExchange fanoutExchange;

    public ConnectionFactory rabbitConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(factoryHost);
        factory.setUsername(factoryUsername);
        factory.setPassword(factoryPassword);
        factory.setVirtualHost(factoryVirtualHost);

        // Configurações de otimização
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(10000);
        factory.setRequestedHeartbeat(60);
        factory.setConnectionTimeout(30000);

        return factory;
    }

    public void initializeExchangeAndQueue() throws IOException, TimeoutException {
        ConnectionFactory factory = rabbitConnectionFactory();
        try (Connection connection = factory.newConnection();
                Channel channel = connection.createChannel()) {

            // usa o mesmo flag de durable do bean FanoutExchange
            channel.exchangeDeclare(fanoutExchange.getName(), fanoutExchange.getType(), fanoutExchange.isDurable());

            // declara fila (durável por padrão aqui)
            channel.queueDeclare(queueName, true, false, false, null);

            // bind (routing key ignorada em fanout)
            channel.queueBind(queueName, fanoutExchange.getName(), "");

            System.out.println("Initialized exchange: " + fanoutExchange.getName() +
                    ", queue: " + queueName +
                    ", exchangeDurable: " + fanoutExchange.isDurable());
        }
    }

    public void basicPublish(String message) {
        try (Connection connection = rabbitConnectionFactory().newConnection();
                Channel channel = connection.createChannel()) {

            // garante declaração com a mesma durabilidade configurada
            channel.exchangeDeclare(fanoutExchange.getName(), fanoutExchange.getType(), fanoutExchange.isDurable());

            channel.basicPublish(fanoutExchange.getName(), "", null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");

        } catch (IOException | TimeoutException e) {
            System.err.println("Error publishing message: " + e.getMessage());
            e.printStackTrace();
        }
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

        try (Connection connection = rabbitConnectionFactory().newConnection();
                Channel channel = connection.createChannel()) {

            // Garante que exchange e fila existem
            channel.exchangeDeclare(fanoutExchange.getName(), fanoutExchange.getType(), true);
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, fanoutExchange.getName(), "");

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

        try (Connection connection = rabbitConnectionFactory().newConnection();
                Channel channel = connection.createChannel()) {

            // Declara o exchange para garantir que existe
            channel.exchangeDeclare(fanoutExchange.getName(), fanoutExchange.getType(), fanoutExchange.isDurable());

            // Declara uma fila anônima temporária para obter informações de binding
            String tempQueueName = channel.queueDeclare().getQueue();

            // Faz bind da fila temporária ao exchange
            channel.queueBind(tempQueueName, fanoutExchange.getName(), "");

            // Tenta obter informações das queues via passive declaration
            // Nota: A RabbitMQ Java Client não fornece um método direto para listar queues de um exchange
            // Então usamos a abordagem de tentar conectar a queues conhecidas

            try {
                // Tenta acessar a fila principal configurada
                com.rabbitmq.client.AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(queueName);
                if (declareOk != null) {
                    queues.add(queueName);
                    System.out.println("Queue found: " + queueName + " (messageCount: " + declareOk.getMessageCount() + ")");
                }
            } catch (IOException e) {
                System.out.println("Queue not found: " + queueName);
            }

            // Remove a fila temporária
            channel.queueDelete(tempQueueName);

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
     * 
     * @param message Conteúdo da mensagem
     * @param customRoutingKey Routing key customizado (ex: "department.financeiro", "department.rh")
     */
    public void publishWithRoutingKey(String message, String customRoutingKey) {
        try (Connection connection = rabbitConnectionFactory().newConnection();
                Channel channel = connection.createChannel()) {

            // Para Topic Exchange
            channel.exchangeDeclare(fanoutExchange.getName(), "topic", fanoutExchange.isDurable());

            channel.basicPublish(fanoutExchange.getName(), customRoutingKey, null, 
                    message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent to '" + customRoutingKey + "': '" + message + "'");

        } catch (IOException | TimeoutException e) {
            System.err.println("Error publishing message with routing key: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Publica mensagem para um departamento específico
     * 
     * @param message Conteúdo da mensagem
     * @param departmentName Nome do departamento
     */
    public void publishToDepartment(String message, String departmentName) {
        String routingKeyForDept = String.format("department.%s", 
                departmentName.toLowerCase().replace(" ", "_"));
        publishWithRoutingKey(message, routingKeyForDept);
    }

    /**
     * Publica mensagem para múltiplos departamentos
     * 
     * @param message Conteúdo da mensagem
     * @param departmentIds Lista de IDs de departamentos
     */
    public void publishToDepartments(String message, List<String> departmentNames) {
        for (String deptName : departmentNames) {
            String routingKey = String.format("department.%s", 
                    deptName.toLowerCase().replace(" ", "_"));
            publishWithRoutingKey(message, routingKey);
        }
    }

    /**
     * Publica mensagem para todos os departamentos (wildcard)
     * Usa "department.#" para alcançar todos os padrões de departamento
     * 
     * @param message Conteúdo da mensagem
     */
    public void publishToAllDepartments(String message) {
        publishWithRoutingKey(message, "department.#");
    }

    /**
     * Publica mensagem para ABSOLUTAMENTE TODOS (departamentos + broadcast)
     * 
     * @param message Conteúdo da mensagem
     */
    public void publishToEveryone(String message) {
        // Envia para todos os departamentos
        publishWithRoutingKey(message, "department.#");
        // Envia broadcast geral
        publishWithRoutingKey(message, "broadcast.#");
    }

    /**
     * Cria uma fila para receber mensagens de um departamento específico
     * 
     * @param departmentName Nome do departamento
     * @return Nome da fila criada
     */
    public String createDepartmentQueue(String departmentName) {
        String deptNameFormatted = departmentName.toLowerCase().replace(" ", "_");
        String queueNameForDept = String.format("queue_department_%s", deptNameFormatted);
        String routingPatternForDept = String.format("department.%s", deptNameFormatted);

        try (Connection connection = rabbitConnectionFactory().newConnection();
                Channel channel = connection.createChannel()) {

            // Declara exchange como Topic
            channel.exchangeDeclare(fanoutExchange.getName(), "topic", fanoutExchange.isDurable());

            // Declara fila para o departamento
            channel.queueDeclare(queueNameForDept, true, false, false, null);

            // Faz bind com routing pattern específico do departamento
            channel.queueBind(queueNameForDept, fanoutExchange.getName(), routingPatternForDept);

            System.out.println("Created queue: " + queueNameForDept + 
                    ", listening to: " + routingPatternForDept);
            return queueNameForDept;

        } catch (IOException | TimeoutException e) {
            System.err.println("Error creating department queue: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Consome mensagens de uma fila de departamento específico
     * 
     * @param queueName Nome da fila do departamento
     * @param timeoutMillis Tempo máximo de espera
     * @return Mensagem recebida ou null se timeout
     */
    public String consumeFromDepartmentQueue(String queueName, long timeoutMillis) {
        BlockingQueue<String> messageQueue = new ArrayBlockingQueue<>(1);

        try (Connection connection = rabbitConnectionFactory().newConnection();
                Channel channel = connection.createChannel()) {

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
