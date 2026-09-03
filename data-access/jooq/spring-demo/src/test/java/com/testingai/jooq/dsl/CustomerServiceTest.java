package com.testingai.jooq.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import(CustomerService.class)
class CustomerServiceTest {

	@Autowired
	private CustomerService customerService;

	@Test
	void createsAndReadsBackACustomer() {
		CustomerView created = customerService.create("Ada Lovelace", "ada@example.com");

		assertThat(created.id()).isNotNull();

		CustomerView found = customerService.findById(created.id());
		assertThat(found).isEqualTo(created);
	}
}
