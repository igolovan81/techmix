package com.testingai.batch.partition;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderRangePartitioner implements Partitioner {

	private static final String SELECT_MIN = "SELECT COALESCE(MIN(id), 0) FROM orders WHERE batch_type = 'PARTITION' AND status = 'PENDING'";
	private static final String SELECT_MAX = "SELECT COALESCE(MAX(id), 0) FROM orders WHERE batch_type = 'PARTITION' AND status = 'PENDING'";

	private final JdbcTemplate jdbcTemplate;

	public OrderRangePartitioner(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Override
	public Map<String, ExecutionContext> partition(int gridSize) {
		Long minId = jdbcTemplate.queryForObject(SELECT_MIN, Long.class);
		Long maxId = jdbcTemplate.queryForObject(SELECT_MAX, Long.class);

		Map<String, ExecutionContext> partitions = new HashMap<>();
		if (minId == 0 && maxId == 0) {
			ExecutionContext context = new ExecutionContext();
			context.putLong("minId", 0L);
			context.putLong("maxId", -1L);
			partitions.put("partition0", context);
			return partitions;
		}

		long targetSize = (maxId - minId) / gridSize + 1;
		long start = minId;
		long end = start + targetSize - 1;

		for (int i = 0; i < gridSize; i++) {
			ExecutionContext context = new ExecutionContext();
			context.putLong("minId", start);
			context.putLong("maxId", Math.min(end, maxId));
			partitions.put("partition" + i, context);
			start = end + 1;
			end = start + targetSize - 1;
		}
		return partitions;
	}
}
