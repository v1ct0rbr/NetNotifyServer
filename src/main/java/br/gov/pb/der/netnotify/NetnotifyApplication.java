package br.gov.pb.der.netnotify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class NetnotifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(NetnotifyApplication.class, args);
	}

}
