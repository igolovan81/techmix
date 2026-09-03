package com.testingai.hibernate.locking;

import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LoanRepository;
import com.testingai.hibernate.repository.MemberRepository;
import com.testingai.hibernate.util.FailureSimulator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CopyCheckoutService {

	private final CopyRepository copyRepository;
	private final MemberRepository memberRepository;
	private final LoanRepository loanRepository;
	private final EntityManager entityManager;

	@Transactional
	public Loan checkoutOptimistic(Long copyId, Long memberId) {
		FailureSimulator.maybeThrow("CopyCheckoutService.checkoutOptimistic");

		Copy copy = copyRepository.findById(copyId)
				.orElseThrow(() -> new NoSuchElementException("Copy not found: " + copyId));
		if (copy.getStatus() != CopyStatus.AVAILABLE) {
			throw new CopyAlreadyLoanedException(copyId);
		}
		copy.setStatus(CopyStatus.ON_LOAN);
		try {
			// saveAndFlush forces the @Version check to run now, inside this method, rather than
			// deferring it to transaction commit — so a conflict surfaces here, not later.
			copyRepository.saveAndFlush(copy);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new CopyAlreadyLoanedException(copyId);
		}
		return createLoan(copy, memberId);
	}

	@Transactional
	public Loan checkoutPessimistic(Long copyId, Long memberId) {
		FailureSimulator.maybeThrow("CopyCheckoutService.checkoutPessimistic");

		Copy copy = entityManager.find(Copy.class, copyId, LockModeType.PESSIMISTIC_WRITE);
		if (copy == null) {
			throw new NoSuchElementException("Copy not found: " + copyId);
		}
		if (copy.getStatus() != CopyStatus.AVAILABLE) {
			throw new CopyAlreadyLoanedException(copyId);
		}
		copy.setStatus(CopyStatus.ON_LOAN);
		return createLoan(copy, memberId);
	}

	private Loan createLoan(Copy copy, Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new NoSuchElementException("Member not found: " + memberId));

		Loan loan = new Loan();
		loan.setCopy(copy);
		loan.setMember(member);
		loan.setLoanDate(LocalDate.now());
		loan.setDueDate(LocalDate.now().plusWeeks(2));
		return loanRepository.save(loan);
	}
}
