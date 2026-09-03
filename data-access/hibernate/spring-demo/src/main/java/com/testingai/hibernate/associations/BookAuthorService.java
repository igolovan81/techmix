package com.testingai.hibernate.associations;

import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.repository.BookRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookAuthorService {

	private final BookRepository bookRepository;
	private final EntityManager entityManager;

	@Transactional(readOnly = true)
	public BookFetchResult demonstrateNPlusOne() {
		statistics().clear();
		List<Book> books = bookRepository.findAll();
		return new BookFetchResult(toViews(books), statistics().getPrepareStatementCount());
	}

	@Transactional(readOnly = true)
	public BookFetchResult findAllWithJoinFetch() {
		statistics().clear();
		List<Book> books = bookRepository.findAllWithAuthorsJoinFetch();
		return new BookFetchResult(toViews(books), statistics().getPrepareStatementCount());
	}

	@Transactional(readOnly = true)
	public BookFetchResult findAllWithEntityGraph() {
		statistics().clear();
		List<Book> books = bookRepository.findAllWithAuthorsEntityGraph();
		return new BookFetchResult(toViews(books), statistics().getPrepareStatementCount());
	}

	private List<BookWithAuthorNames> toViews(List<Book> books) {
		return books.stream()
				.map(book -> new BookWithAuthorNames(
						book.getId(),
						book.getTitle(),
						book.getAuthors().stream().map(Author::getName).toList()))
				.toList();
	}

	private Statistics statistics() {
		return entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
	}
}
