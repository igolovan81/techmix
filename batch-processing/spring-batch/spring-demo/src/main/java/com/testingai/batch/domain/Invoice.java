package com.testingai.batch.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

	private Long id;
	private Long orderId;
	private String customerId;
	private BigDecimal amount;
	private BigDecimal tax;
	private BigDecimal total;
	private LocalDateTime createdAt;
}
