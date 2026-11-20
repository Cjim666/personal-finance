package com.java.dev.jim.personal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:personal-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.flyway.enabled=true"
})
class PersonalApplicationTests {

	@Test
	void contextLoads() {
	}

}
