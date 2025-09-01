package br.gov.pb.der.netnotify;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NetnotifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(NetnotifyApplication.class, args);
	}

	@Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange("netnotify");
    }

}
