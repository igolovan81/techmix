package com.testingai.batch.domain;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

public class OrderRowMapper implements RowMapper<Order> {

	@Override
	public Order mapRow(ResultSet rs, int rowNum) throws SQLException {
		Order order = new Order();
		order.setId(rs.getLong("id"));
		order.setBatchType(BatchType.valueOf(rs.getString("batch_type")));
		order.setCustomerId(rs.getString("customer_id"));
		order.setAmount(rs.getBigDecimal("amount"));
		order.setStatus(OrderStatus.valueOf(rs.getString("status")));
		order.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
		return order;
	}
}
