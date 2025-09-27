package br.gov.pb.der.netnotify.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
}
