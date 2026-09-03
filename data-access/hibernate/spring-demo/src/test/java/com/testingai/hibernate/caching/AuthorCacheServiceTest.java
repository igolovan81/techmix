package com.testingai.hibernate.caching;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.repository.AuthorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(AuthorCacheService.class)
// @DataJpaTest wraps each test method in a transaction that rolls back at the end; NOT_SUPPORTED
// disables that here so @BeforeEach's save() actually commits, making the seeded Author visible to
// AuthorCacheService's own REQUIRES_NEW transactions (a separate physical transaction can't see
// another transaction's uncommitted rows).
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthorCacheServiceTest {

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private AuthorCacheService authorCacheService;

	private Long authorId;

	@BeforeEach
	void seedOneAuthor() {
		Author author = new Author();
		author.setName("Isaac Asimov");
		authorId = authorRepository.save(author).getId();
	}

	@Test
	void secondReadInSameTransactionHitsFirstLevelCacheNotTheDatabase() {
		L1Result result = authorCacheService.readTwiceInSameTransaction(authorId);

		assertThat(result.firstReadName()).isEqualTo("Isaac Asimov");
		assertThat(result.secondReadName()).isEqualTo("Isaac Asimov");
		// exactly one SELECT for both reads — the second is served from the persistence context (L1)
		assertThat(result.sqlStatementCount()).isEqualTo(1);
	}

	@Test
	void secondReadInANewTransactionHitsSecondLevelCache() {
		L2Result result = authorCacheService.readAcrossTwoTransactions(authorId);

		assertThat(result.missesAfterFirstRead()).isEqualTo(1);
		assertThat(result.hitsAfterFirstRead()).isEqualTo(0);
		assertThat(result.missesAfterSecondRead()).isEqualTo(1);
		assertThat(result.hitsAfterSecondRead()).isEqualTo(1);
	}
}
