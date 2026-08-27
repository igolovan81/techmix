package com.testingai.batch.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import com.testingai.batch.domain.BatchType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderSeedService {

	private static final String INSERT = "INSERT INTO orders (batch_type, customer_id, amount, status) VALUES (?, ?, ?, 'PENDING')";

	private final JdbcTemplate jdbcTemplate;

	public int seed(BatchType batchType, int count) {
		List<Object[]> batchArgs = IntStream.range(0, count)
				.mapToObj(i -> new Object[]{batchType.name(), "cust-" + UUID.randomUUID(), randomAmount()}).toList();
		int[] results = jdbcTemplate.batchUpdate(INSERT, batchArgs);
		return results.length;
	}

	private BigDecimal randomAmount() {
		return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(10, 500)).setScale(2, RoundingMode.HALF_UP);
	}
}
