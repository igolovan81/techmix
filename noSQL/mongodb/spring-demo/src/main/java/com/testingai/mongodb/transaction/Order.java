package com.testingai.mongodb.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order {

	@Id
	private String id;
	private String productId;
	private int quantity;
	private double unitPrice;
	private double lineTotal;
	private String status;
}
