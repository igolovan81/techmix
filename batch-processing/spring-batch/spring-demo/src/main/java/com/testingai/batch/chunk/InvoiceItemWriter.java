package com.testingai.batch.chunk;

import java.util.List;

import com.testingai.batch.domain.Invoice;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceItemWriter implements ItemWriter<Invoice> {

	private static final String INSERT_INVOICE = "INSERT INTO invoices (order_id, customer_id, amount, tax, total) VALUES (?, ?, ?, ?, ?)";
	private static final String MARK_INVOICED = "UPDATE orders SET status = 'INVOICED' WHERE id = ?";

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void write(Chunk<? extends Invoice> chunk) {
		List<? extends Invoice> invoices = chunk.getItems();

		jdbcTemplate.batchUpdate(INSERT_INVOICE, invoices, invoices.size(), (ps, invoice) -> {
			ps.setLong(1, invoice.getOrderId());
			ps.setString(2, invoice.getCustomerId());
			ps.setBigDecimal(3, invoice.getAmount());
			ps.setBigDecimal(4, invoice.getTax());
			ps.setBigDecimal(5, invoice.getTotal());
		});

		jdbcTemplate.batchUpdate(MARK_INVOICED, invoices, invoices.size(),
				(ps, invoice) -> ps.setLong(1, invoice.getOrderId()));
	}
}
