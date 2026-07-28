package com.testingai.grpc.server.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDataServiceTest {

	private final SampleDataService service = new SampleDataService();

	@Test
	void findProduct_returnsProduct_whenIdKnown() {
		assertThat(service.findProduct("p1")).isPresent().get().extracting("name").isEqualTo("Mini Widget");
	}

	@Test
	void findProduct_returnsEmpty_whenIdUnknown() {
		assertThat(service.findProduct("unknown")).isEmpty();
	}

	@Test
	void listProducts_returnsAllFortySampleProducts() {
		assertThat(service.listProducts()).hasSize(40);
	}
}
