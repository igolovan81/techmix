package com.testingai.batch.chunk;

import java.util.ArrayList;
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
	// Conditional, not unconditional: this is a claim, not just a status flip. Multiple readers can
	// legitimately see the same PENDING rows if the same job pattern is launched concurrently (nothing
	// prevents that -- each launch gets its own unique JobParameters so it can run independently). Only
	// the execution whose UPDATE actually flips a row from PENDING wins the right to invoice it; a
	// concurrent execution that loses the race sees 0 affected rows for that order and must not insert
	// a duplicate invoice for it.
	private static final String CLAIM_ORDER = "UPDATE orders SET status = 'INVOICED' WHERE id = ? AND status = 'PENDING'";

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void write(Chunk<? extends Invoice> chunk) {
		List<? extends Invoice> invoices = chunk.getItems();
		if (invoices.isEmpty()) {
			return;
		}

		// batchUpdate(sql, Collection<T>, batchSize, pss) returns int[][] -- one row per internal batch
		// chunk. Since batchSize == invoices.size(), everything runs as a single batch, so exactly one
		// row comes back.
		int[] claimed = jdbcTemplate.batchUpdate(CLAIM_ORDER, invoices, invoices.size(),
				(ps, invoice) -> ps.setLong(1, invoice.getOrderId()))[0];

		List<Invoice> claimedInvoices = new ArrayList<>();
		for (int i = 0; i < invoices.size(); i++) {
			if (claimed[i] > 0) {
				claimedInvoices.add(invoices.get(i));
			}
		}
		if (claimedInvoices.isEmpty()) {
			return;
		}

		jdbcTemplate.batchUpdate(INSERT_INVOICE, claimedInvoices, claimedInvoices.size(), (ps, invoice) -> {
			ps.setLong(1, invoice.getOrderId());
			ps.setString(2, invoice.getCustomerId());
			ps.setBigDecimal(3, invoice.getAmount());
			ps.setBigDecimal(4, invoice.getTax());
			ps.setBigDecimal(5, invoice.getTotal());
		});
	}
}
