package com.testingai.graphql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageEntity {

	@Id
	@Column(name = "product_id")
	private Long productId;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "data", nullable = false)
	private byte[] data;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
