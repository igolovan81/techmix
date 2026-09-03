package com.testingai.jooq.nested;

import static com.testingai.jooq.generated.Tables.ORDERS;
import static com.testingai.jooq.generated.Tables.ORDER_ITEM;
import static com.testingai.jooq.generated.Tables.PRODUCT;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

	private final DSLContext ctx;

	public Optional<OrderWithItems> findOrderWithItems(Long orderId) {
		return ctx
				.select(ORDERS.ID, ORDERS.CUSTOMER_ID, ORDERS.PLACED_AT,
						multiset(select(ORDER_ITEM.PRODUCT_ID, PRODUCT.NAME, ORDER_ITEM.QUANTITY, ORDER_ITEM.UNIT_PRICE)
								.from(ORDER_ITEM).join(PRODUCT).on(ORDER_ITEM.PRODUCT_ID.eq(PRODUCT.ID))
								.where(ORDER_ITEM.ORDER_ID.eq(ORDERS.ID)))
								.convertFrom(r -> r.map(Records.mapping(OrderItemView::new))))
				.from(ORDERS).where(ORDERS.ID.eq(orderId)).fetchOptional(Records.mapping(OrderWithItems::new));
	}
}
