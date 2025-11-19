package br.gov.pb.der.netnotify.config;

import javax.annotation.PostConstruct;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${spring.rabbitmq.exchange:${RABBITMQ_EXCHANGE:netnotify_topic}}")
    private String exchangeName;

    @Value("${spring.rabbitmq.queue:${RABBITMQ_QUEUE:notification_queue}}")
    private String queueName;

    @Value("${spring.rabbitmq.routing-key:${RABBITMQ_ROUTING_KEY:notification_key}}")
    private String routingKey;

    @Value("${spring.rabbitmq.exchange-durable:${RABBITMQ_EXCHANGE_DURABLE:false}}")
    private boolean exchangeDurable;

    @Bean
    public TopicExchange fanoutExchange() {
        return new TopicExchange(exchangeName, exchangeDurable, false);
    }

    @Bean
    public String queueName() {
        return queueName;
    }

    @Bean
    public String routingKey() {
        return routingKey;
    }

    @PostConstruct
    public void debug() {
        System.out.println("RabbitConfig: exchange=" + exchangeName + ", durable=" + exchangeDurable +
                ", queue=" + queueName + ", routingKey='" + routingKey + "'");
    }
}