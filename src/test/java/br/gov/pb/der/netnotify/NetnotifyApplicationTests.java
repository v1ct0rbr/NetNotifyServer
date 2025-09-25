package br.gov.pb.der.netnotify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import br.gov.pb.der.netnotify.TestConfig;

@SpringBootTest
@Import(TestConfig.class)
class NetnotifyApplicationTests {

	@Test
	void contextLoads() {
	}

}
