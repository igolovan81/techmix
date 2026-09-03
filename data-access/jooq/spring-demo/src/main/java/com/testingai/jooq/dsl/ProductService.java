package com.testingai.jooq.dsl;

import static com.testingai.jooq.generated.Tables.PRODUCT;

import com.testingai.jooq.generated.tables.records.ProductRecord;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final DSLContext ctx;

	public ProductView create(Long categoryId, String name, BigDecimal price, Integer stock) {
		var record = ctx.insertInto(PRODUCT, PRODUCT.CATEGORY_ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK)
				.values(categoryId, name, price, stock).returning(PRODUCT.ID).fetchOne();
		return new ProductView(record.getId(), categoryId, name, price, stock);
	}

	public ProductView findById(Long id) {
		return ctx.selectFrom(PRODUCT).where(PRODUCT.ID.eq(id)).fetchOptional().map(this::toView)
				.orElseThrow(() -> new NoSuchElementException("Product not found: " + id));
	}

	public ProductView update(Long id, Long categoryId, String name, BigDecimal price, Integer stock) {
		ctx.update(PRODUCT).set(PRODUCT.CATEGORY_ID, categoryId).set(PRODUCT.NAME, name).set(PRODUCT.PRICE, price)
				.set(PRODUCT.STOCK, stock).where(PRODUCT.ID.eq(id)).execute();
		return new ProductView(id, categoryId, name, price, stock);
	}

	public void delete(Long id) {
		ctx.deleteFrom(PRODUCT).where(PRODUCT.ID.eq(id)).execute();
	}

	private ProductView toView(ProductRecord r) {
		return new ProductView(r.getId(), r.getCategoryId(), r.getName(), r.getPrice(), r.getStock());
	}
}
