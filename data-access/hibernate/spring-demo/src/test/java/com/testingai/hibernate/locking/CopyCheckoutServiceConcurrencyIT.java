package com.testingai.hibernate.locking;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("postgres")
class CopyCheckoutServiceConcurrencyIT {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("hibernatedemo")
			.withUsername("hibernate")
			.withPassword("hibernate");

	@DynamicPropertySource
	static void overrideDatasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private CopyRepository copyRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private CopyCheckoutService copyCheckoutService;

	private Long copyId;
	private Long firstMemberId;
	private Long secondMemberId;

	@BeforeEach
	void seedOneAvailableCopyAndTwoMembers() {
		Book book = new Book();
		book.setTitle("Concurrent checkout target");
		book.setPublicationYear(2020);
		libraryItemRepository.save(book);

		Copy copy = new Copy();
		copy.setLibraryItem(book);
		copy.setBarcode("BC-CONCURRENT-1");
		copy.setStatus(CopyStatus.AVAILABLE);
		copyId = copyRepository.save(copy).getId();

		Member first = new Member();
		first.setName("First Caller");
		first.setEmail("first@example.com");
		first.setMembershipDate(LocalDate.now());
		firstMemberId = memberRepository.save(first).getId();

		Member second = new Member();
		second.setName("Second Caller");
		second.setEmail("second@example.com");
		second.setMembershipDate(LocalDate.now());
		secondMemberId = memberRepository.save(second).getId();
	}

	@Test
	void pessimisticLockingSerializesConcurrentCheckoutsOfTheSameCopy() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Boolean> firstCheckout = () -> checkoutIgnoringSimulatedFailures(firstMemberId);
			Callable<Boolean> secondCheckout = () -> checkoutIgnoringSimulatedFailures(secondMemberId);

			List<Future<Boolean>> results = executor.invokeAll(List.of(firstCheckout, secondCheckout));
			boolean firstSucceeded = results.get(0).get();
			boolean secondSucceeded = results.get(1).get();

			// exactly one of the two concurrent callers succeeds; the other's post-lock read finds
			// the copy already ON_LOAN and throws CopyAlreadyLoanedException — no double-checkout.
			assertThat(firstSucceeded ^ secondSucceeded).isTrue();
		} finally {
			executor.shutdown();
		}
	}

	private boolean checkoutIgnoringSimulatedFailures(Long memberId) {
		while (true) {
			try {
				Loan loan = copyCheckoutService.checkoutPessimistic(copyId, memberId);
				return loan.getId() != null;
			} catch (CopyAlreadyLoanedException e) {
				return false;
			} catch (RuntimeException simulatedFailure) {
				// retry past FailureSimulator's random 5% failure
			}
		}
	}
}
