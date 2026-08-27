package com.testingai.cassandra.datamodeling;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.testingai.cassandra.counter.OrderCountService;
import com.testingai.cassandra.crud.Product;
import com.testingai.cassandra.lwt.StockReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.stereotype.Service;

import static org.springframework.data.cassandra.core.query.Criteria.where;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final CassandraTemplate cassandraTemplate;
	private final StockReservationService stockReservationService;
	private final OrderCountService orderCountService;

	public OrderByCustomer placeOrder(String customerId, UUID productId, int quantity) {
		Product product = stockReservationService.decrementIfAvailable(productId, quantity);

		UUID orderId = Uuids.timeBased();
		BigDecimal unitPrice = product.getPrice();
		BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

		OrderByCustomer byCustomer = new OrderByCustomer(customerId, orderId, productId, quantity, unitPrice,
				lineTotal);
		OrderByProduct byProduct = new OrderByProduct(productId, orderId, customerId, quantity, unitPrice, lineTotal);
		cassandraTemplate.insert(byCustomer);
		cassandraTemplate.insert(byProduct);

		orderCountService.increment(productId);

		return byCustomer;
	}

	public List<OrderByCustomer> findByCustomer(String customerId) {
		return cassandraTemplate.select(Query.query(where("customer_id").is(customerId)), OrderByCustomer.class);
	}

	public List<OrderByProduct> findByProduct(UUID productId) {
		return cassandraTemplate.select(Query.query(where("product_id").is(productId)), OrderByProduct.class);
	}
}
