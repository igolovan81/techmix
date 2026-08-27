package com.testingai.cassandra.crud;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("products")
public class Product {

	@PrimaryKey
	private UUID id;
	private String name;
	private BigDecimal price;
	private int stock;
}
