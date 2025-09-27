package br.gov.pb.der.netnotify.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    @Autowired
    private FanoutExchange fanoutExchange;

    public ConnectionFactory rabbitConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(factoryHost);
        factory.setUsername(factoryUsername);
        factory.setPassword(factoryPassword);
        factory.setVirtualHost(factoryVirtualHost);

        return factory;
    }

    public void basicPublish(String message) {
        try {
            ConnectionFactory factory = rabbitConnectionFactory();
            com.rabbitmq.client.Connection connection = factory.newConnection();
            com.rabbitmq.client.Channel channel = connection.createChannel();
            channel.exchangeDeclare(fanoutExchange.getName(), fanoutExchange.getType());

            channel.basicPublish(fanoutExchange.getName(), "", null, message.getBytes("UTF-8"));
            System.out.println(" [x] Sent '" + message + "'");

            channel.close();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String basicConsume() {
        return basicConsume(5000);
    }

    /**
     * Consome de forma síncrona a primeira mensagem disponível na exchange
     * (fanout).
     * Aguarda até timeoutMillis milissegundos antes de retornar null.
     */
    public String basicConsume(long timeoutMillis) {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        try {
            ConnectionFactory factory = rabbitConnectionFactory();
            com.rabbitmq.client.Connection connection = factory.newConnection();
            com.rabbitmq.client.Channel channel = connection.createChannel();
            channel.exchangeDeclare(fanoutExchange.getName(), fanoutExchange.getType());
            String queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, fanoutExchange.getName(), "");

            System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

            com.rabbitmq.client.DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println(" [x] Received '" + msg + "'");
                // offer não bloqueante
                queue.offer(msg);
            };

            String consumerTag = channel.basicConsume(queueName, true, deliverCallback, consumerTag1 -> {
            });

            String message = queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);

            // cleanup
            try {
                if (consumerTag != null)
                    channel.basicCancel(consumerTag);
            } catch (Exception ignore) {
            }
            try {
                channel.close();
            } catch (Exception ignore) {
            }
            try {
                connection.close();
            } catch (Exception ignore) {
            }

            return message != null ? message : null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
