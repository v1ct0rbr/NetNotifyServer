package br.gov.pb.der.netnotify.config;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${spring.rabbitmq.exchange:fannout}")
    private String exchangeName;

    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange(exchangeName, true, false);
    }
}