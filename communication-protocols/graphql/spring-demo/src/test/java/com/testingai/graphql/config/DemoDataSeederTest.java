package com.testingai.graphql.config;

import com.testingai.graphql.repository.CategoryRepository;
import com.testingai.graphql.repository.OrderRepository;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ReviewRepository;
import com.testingai.graphql.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DemoDataSeederTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ReviewRepository reviewRepository;
	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private SeedProperties seedProperties;
	@Autowired
	private DemoDataSeeder seeder;

	@Test
	void seededData_matchesConfiguredVolumes() {
		assertThat(userRepository.count()).isEqualTo(seedProperties.userCount());
		assertThat(categoryRepository.count()).isEqualTo(seedProperties.categoryCount());
		assertThat(productRepository.count()).isEqualTo(seedProperties.productCount());
		assertThat(orderRepository.count()).isEqualTo(seedProperties.orderCount());
	}

	@Test
	void seededUsers_includeTheTwoSecurityDemoAccounts() {
		assertThat(userRepository.findByUsername("user")).isPresent();
		assertThat(userRepository.findByUsername("admin")).isPresent();
	}

	@Test
	void everyProductsReviewTotal_fallsWithinTheConfiguredAggregateRange() {
		long productCount = productRepository.count();
		long totalReviews = reviewRepository.count();

		assertThat(totalReviews).isBetween(productCount * seedProperties.minReviewsPerProduct(),
				productCount * seedProperties.maxReviewsPerProduct());
	}

	@Test
	void rerunningSeeder_isNoOp_whenDataAlreadyPresent() {
		long usersBefore = userRepository.count();

		seeder.run(null);

		assertThat(userRepository.count()).isEqualTo(usersBefore);
	}
}
