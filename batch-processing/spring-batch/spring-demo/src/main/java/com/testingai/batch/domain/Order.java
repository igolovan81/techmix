package com.testingai.batch.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

	private Long id;
	private BatchType batchType;
	private String customerId;
	private BigDecimal amount;
	private OrderStatus status;
	private LocalDateTime createdAt;
}
