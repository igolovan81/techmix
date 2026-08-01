package com.testingai.websockets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WsClientStaticResourceTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void wsClientPage_isServed() {
		ResponseEntity<String> response = restTemplate.getForEntity("/ws-client/index.html", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("WebSocket Demo");
	}
}
