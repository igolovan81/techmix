package com.testingai.jooq.dsl;

import static com.testingai.jooq.generated.Tables.CUSTOMER;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

	private final DSLContext ctx;

	public CustomerView create(String name, String email) {
		var record = ctx.insertInto(CUSTOMER, CUSTOMER.NAME, CUSTOMER.EMAIL).values(name, email).returning(CUSTOMER.ID)
				.fetchOne();
		return new CustomerView(record.getId(), name, email);
	}

	public CustomerView findById(Long id) {
		return ctx.selectFrom(CUSTOMER).where(CUSTOMER.ID.eq(id)).fetchOptional()
				.map(r -> new CustomerView(r.getId(), r.getName(), r.getEmail()))
				.orElseThrow(() -> new NoSuchElementException("Customer not found: " + id));
	}
}
