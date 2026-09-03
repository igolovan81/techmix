package com.testingai.hibernate.caching;

import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.repository.AuthorRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthorCacheService {

	private final AuthorRepository authorRepository;
	private final EntityManager entityManager;
	private final TransactionTemplate transactionTemplate;

	public AuthorCacheService(
			AuthorRepository authorRepository,
			EntityManager entityManager,
			PlatformTransactionManager transactionManager) {
		this.authorRepository = authorRepository;
		this.entityManager = entityManager;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		// REQUIRES_NEW: each call must open a genuinely separate physical transaction/session so
		// readAcrossTwoTransactions demonstrates cross-session L2 behavior rather than just reusing
		// whatever transaction (e.g. a @DataJpaTest test transaction) happens to already be open.
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public L1Result readTwiceInSameTransaction(Long authorId) {
		statistics().clear();
		return transactionTemplate.execute(status -> {
			Author first = authorRepository.findById(authorId).orElseThrow();
			Author second = authorRepository.findById(authorId).orElseThrow();
			return new L1Result(first.getName(), second.getName(), statistics().getPrepareStatementCount());
		});
	}

	public L2Result readAcrossTwoTransactions(Long authorId) {
		statistics().clear();
		transactionTemplate.executeWithoutResult(status -> authorRepository.findById(authorId).orElseThrow());
		long missesAfterFirstRead = statistics().getSecondLevelCacheMissCount();
		long hitsAfterFirstRead = statistics().getSecondLevelCacheHitCount();

		transactionTemplate.executeWithoutResult(status -> authorRepository.findById(authorId).orElseThrow());
		long missesAfterSecondRead = statistics().getSecondLevelCacheMissCount();
		long hitsAfterSecondRead = statistics().getSecondLevelCacheHitCount();

		return new L2Result(missesAfterFirstRead, hitsAfterFirstRead, missesAfterSecondRead, hitsAfterSecondRead);
	}

	private Statistics statistics() {
		return entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
	}
}
