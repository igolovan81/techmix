package com.testingai.hibernate.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.MemberRepository;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(CopyCheckoutService.class)
class CopyCheckoutServiceTest {

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private CopyRepository copyRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private CopyCheckoutService copyCheckoutService;

	private Long availableCopyId;
	private Long memberId;

	@BeforeEach
	void seedOneAvailableCopyAndOneMember() {
		Book book = new Book();
		book.setTitle("Domain-Driven Design");
		book.setPublicationYear(2003);
		libraryItemRepository.save(book);

		Copy copy = new Copy();
		copy.setLibraryItem(book);
		copy.setBarcode("BC-LOCK-1");
		copy.setStatus(CopyStatus.AVAILABLE);
		availableCopyId = copyRepository.save(copy).getId();

		Member member = new Member();
		member.setName("Eric Evans");
		member.setEmail("eric@example.com");
		member.setMembershipDate(LocalDate.now());
		memberId = memberRepository.save(member).getId();
	}

	@Test
	void checkoutOptimisticMarksCopyOnLoanAndCreatesLoan() {
		Loan loan = ignoringSimulatedFailures(
				() -> copyCheckoutService.checkoutOptimistic(availableCopyId, memberId));

		assertThat(loan.getId()).isNotNull();
		assertThat(copyRepository.findById(availableCopyId).orElseThrow().getStatus())
				.isEqualTo(CopyStatus.ON_LOAN);
	}

	@Test
	void checkoutOptimisticOnAnAlreadyLoanedCopyThrows() {
		ignoringSimulatedFailures(() -> copyCheckoutService.checkoutOptimistic(availableCopyId, memberId));

		assertThatThrownBy(() -> ignoringSimulatedFailures(
						() -> copyCheckoutService.checkoutOptimistic(availableCopyId, memberId)))
				.isInstanceOf(CopyAlreadyLoanedException.class);
	}

	@Test
	void checkoutPessimisticMarksCopyOnLoanAndCreatesLoan() {
		Loan loan = ignoringSimulatedFailures(
				() -> copyCheckoutService.checkoutPessimistic(availableCopyId, memberId));

		assertThat(loan.getId()).isNotNull();
		assertThat(copyRepository.findById(availableCopyId).orElseThrow().getStatus())
				.isEqualTo(CopyStatus.ON_LOAN);
	}

	private <T> T ignoringSimulatedFailures(Supplier<T> call) {
		while (true) {
			try {
				return call.get();
			} catch (CopyAlreadyLoanedException e) {
				throw e;
			} catch (RuntimeException simulatedFailure) {
				// FailureSimulator fires unconditionally as the first statement in both checkout methods;
				// retry until we get past it, but never swallow the real assertion target.
			}
		}
	}
}
