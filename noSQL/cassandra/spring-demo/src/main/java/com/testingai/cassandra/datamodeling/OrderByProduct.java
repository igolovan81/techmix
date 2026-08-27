package com.testingai.cassandra.datamodeling;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("orders_by_product")
public class OrderByProduct {

	@PrimaryKeyColumn(name = "product_id", type = PrimaryKeyType.PARTITIONED)
	private UUID productId;

	@PrimaryKeyColumn(name = "order_id", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
	private UUID orderId;

	@Column("customer_id")
	private String customerId;

	private int quantity;

	@Column("unit_price")
	private BigDecimal unitPrice;

	@Column("line_total")
	private BigDecimal lineTotal;
}
