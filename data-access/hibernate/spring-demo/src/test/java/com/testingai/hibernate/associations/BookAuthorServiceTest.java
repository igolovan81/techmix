package com.testingai.hibernate.associations;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.repository.AuthorRepository;
import com.testingai.hibernate.repository.BookRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(BookAuthorService.class)
class BookAuthorServiceTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private BookAuthorService bookAuthorService;

	@BeforeEach
	void seedThreeBooksEachWithOneAuthor() {
		for (int i = 0; i < 3; i++) {
			Author author = new Author();
			author.setName("Author " + i);
			authorRepository.save(author);

			Book book = new Book();
			book.setTitle("Book " + i);
			book.setPublicationYear(2000 + i);
			book.setAuthors(Set.of(author));
			bookRepository.save(book);
		}
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void naiveFetchExecutesOneSelectPerBookForAuthors() {
		BookFetchResult result = bookAuthorService.demonstrateNPlusOne();

		assertThat(result.books()).hasSize(3);
		// 1 select for findAll() + 3 selects, one per book, to lazily load its authors collection
		assertThat(result.sqlStatementCount()).isEqualTo(4);
	}

	@Test
	void joinFetchExecutesExactlyOneSelect() {
		BookFetchResult result = bookAuthorService.findAllWithJoinFetch();

		assertThat(result.books()).hasSize(3);
		assertThat(result.sqlStatementCount()).isEqualTo(1);
	}

	@Test
	void entityGraphExecutesExactlyOneSelect() {
		BookFetchResult result = bookAuthorService.findAllWithEntityGraph();

		assertThat(result.books()).hasSize(3);
		assertThat(result.sqlStatementCount()).isEqualTo(1);
	}
}
