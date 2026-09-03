package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Book;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long> {

	@Query("select distinct b from Book b left join fetch b.authors")
	List<Book> findAllWithAuthorsJoinFetch();

	@EntityGraph(attributePaths = "authors")
	@Query("select b from Book b")
	List<Book> findAllWithAuthorsEntityGraph();
}
