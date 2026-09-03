package com.testingai.jooq.batch;

import static com.testingai.jooq.generated.Tables.ORDER_ITEM;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderItemBatchService {

	private final DSLContext ctx;

	public int addItemsBatch(Long orderId, List<BatchOrderItemRequest> items) {
		var inserts = items.stream()
				.map(item -> ctx
						.insertInto(ORDER_ITEM, ORDER_ITEM.ORDER_ID, ORDER_ITEM.PRODUCT_ID, ORDER_ITEM.QUANTITY,
								ORDER_ITEM.UNIT_PRICE)
						.values(orderId, item.productId(), item.quantity(), item.unitPrice()))
				.toList();

		int[] results = ctx.batch(inserts).execute();
		return Arrays.stream(results).sum();
	}
}
