package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.exception.InsufficientStockException;
import com.testingai.graphql.repository.OrderItemRepository;
import com.testingai.graphql.repository.OrderRepository;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// NOT_SUPPORTED: @DataJpaTest normally wraps each test in a transaction it rolls back afterward, but that would
// make OrderService's own @Transactional just join that already-open transaction instead of getting its own — so
// a mid-method exception would only mark it rollback-only, and the "nothing was persisted" assertions below would
// still see the not-yet-rolled-back, in-flight changes within the SAME transaction. Disabling the wrapper transaction
// lets OrderService.placeOrder's @Transactional commit/rollback for real, independently, which is what these tests
// need to observe.
//
// @Import(OrderService.class): @DataJpaTest doesn't component-scan @Service beans, so without this OrderService
// would have to be constructed manually with "new" — a plain object with no Spring AOP proxy, meaning its
// @Transactional annotation would never actually start a transaction. That's exactly what surfaced here: the
// pessimistic write lock in ProductRepository.findByIdForUpdate requires an active transaction to exist at all
// (a hard Hibernate 6 requirement, not just a consistency nicety), so it fails outright without one. @Import
// registers OrderService as a real Spring bean in this test's context, wrapped by the transactional proxy.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(OrderService.class)
class OrderServiceTest {

	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private OrderItemRepository orderItemRepository;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private OrderService orderService;

	private ProductEntity product;
	private UserEntity user;

	@BeforeEach
	void setUp() {
		user = new UserEntity();
		user.setUsername("jordan-" + System.nanoTime());
		user.setEmail("jordan@example.com");
		user.setDisplayName("Jordan");
		user.setRole(Role.CUSTOMER);
		user = userRepository.save(user);

		product = new ProductEntity();
		product.setName("Widget");
		product.setPriceCents(999);
		product.setStockQty(5);
		product = productRepository.save(product);
	}

	@Test
	void placeOrder_decrementsStock_andComputesTotal() {
		Order order = orderService.placeOrder(user.getUsername(),
				List.of(new OrderItemInput(product.getId().toString(), 2)));

		assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
		assertThat(order.totalCents()).isEqualTo(1998);
		assertThat(productRepository.findById(product.getId())).get().extracting(ProductEntity::getStockQty)
				.isEqualTo(3);
	}

	@Test
	void placeOrder_throwsInsufficientStock_andPersistsNothing_whenQuantityExceedsStock() {
		assertThatThrownBy(() -> orderService.placeOrder(user.getUsername(),
				List.of(new OrderItemInput(product.getId().toString(), 99))))
				.isInstanceOf(InsufficientStockException.class);

		assertThat(orderRepository.findByUserId(user.getId())).isEmpty();
		assertThat(productRepository.findById(product.getId())).get().extracting(ProductEntity::getStockQty)
				.isEqualTo(5);
	}

	@Test
	void placeOrder_rollsBackEarlierDecrements_whenALaterItemFails() {
		ProductEntity secondProductToSave = new ProductEntity();
		secondProductToSave.setName("Gadget");
		secondProductToSave.setPriceCents(500);
		secondProductToSave.setStockQty(1);
		ProductEntity secondProduct = productRepository.save(secondProductToSave);

		assertThatThrownBy(() -> orderService.placeOrder(user.getUsername(),
				List.of(new OrderItemInput(product.getId().toString(), 1),
						new OrderItemInput(secondProduct.getId().toString(), 99))))
				.isInstanceOf(InsufficientStockException.class);

		assertThat(productRepository.findById(product.getId())).get().extracting(ProductEntity::getStockQty)
				.isEqualTo(5);
		assertThat(orderRepository.findByUserId(user.getId())).isEmpty();
	}

	@Test
	void placeOrder_throws_whenUserUnknown() {
		assertThatThrownBy(() -> orderService.placeOrder("nobody-" + System.nanoTime(), List.of()))
				.isInstanceOf(NoSuchElementException.class);
	}

	@Test
	void updateOrderStatus_changesStatus() {
		Order placed = orderService.placeOrder(user.getUsername(),
				List.of(new OrderItemInput(product.getId().toString(), 1)));

		Order updated = orderService.updateOrderStatus(placed.id(), OrderStatus.SHIPPED);

		assertThat(updated.status()).isEqualTo(OrderStatus.SHIPPED);
	}
}
