package com.testingai.jooq.transactions;

import static com.testingai.jooq.generated.Tables.ORDERS;
import static com.testingai.jooq.generated.Tables.ORDER_ITEM;
import static com.testingai.jooq.generated.Tables.PRODUCT;

import com.testingai.jooq.util.FailureSimulator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPlacementService {

	private final DSLContext ctx;

	public OrderPlaced placeOrder(PlaceOrderRequest request) {
		return ctx.transactionResult(configuration -> {
			var tx = DSL.using(configuration);

			FailureSimulator.maybeThrow("order-placement");

			LocalDateTime placedAt = LocalDateTime.now();
			Long orderId = tx.insertInto(ORDERS, ORDERS.CUSTOMER_ID, ORDERS.PLACED_AT)
					.values(request.customerId(), placedAt).returning(ORDERS.ID).fetchOne().getId();

			for (OrderLineRequest line : request.lines()) {
				var product = tx.selectFrom(PRODUCT).where(PRODUCT.ID.eq(line.productId())).forUpdate().fetchOne();

				if (product == null || product.getStock() < line.quantity()) {
					throw new InsufficientStockException(line.productId());
				}

				tx.update(PRODUCT).set(PRODUCT.STOCK, product.getStock() - line.quantity())
						.where(PRODUCT.ID.eq(line.productId())).execute();

				tx.insertInto(ORDER_ITEM, ORDER_ITEM.ORDER_ID, ORDER_ITEM.PRODUCT_ID, ORDER_ITEM.QUANTITY,
						ORDER_ITEM.UNIT_PRICE).values(orderId, line.productId(), line.quantity(), product.getPrice())
						.execute();
			}

			return new OrderPlaced(orderId, request.customerId(), placedAt);
		});
	}
}
