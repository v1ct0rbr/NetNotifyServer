package br.gov.pb.der.netnotify;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import br.gov.pb.der.netnotify.service.RabbitmqService;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class NetnotifyApplication {

	@Autowired
	private RabbitmqService rabbitmqService;

	public static void main(String[] args) {
		SpringApplication.run(NetnotifyApplication.class, args);
	}

	@PostConstruct
	public void initializeRabbitMQ() {
		try {
			rabbitmqService.initializeExchangeAndQueue();
			System.out.println("RabbitMQ exchange and queue initialized successfully");
		} catch (Exception e) {
			System.err.println("Failed to initialize RabbitMQ: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
