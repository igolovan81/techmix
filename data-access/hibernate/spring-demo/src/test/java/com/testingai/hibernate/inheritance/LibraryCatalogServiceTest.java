package com.testingai.hibernate.inheritance;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Dvd;
import com.testingai.hibernate.entity.Magazine;
import com.testingai.hibernate.repository.LibraryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(LibraryCatalogService.class)
class LibraryCatalogServiceTest {

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private LibraryCatalogService libraryCatalogService;

	@BeforeEach
	void seedOneOfEachSubtype() {
		Book book = new Book();
		book.setTitle("Clean Code");
		book.setPublicationYear(2008);
		libraryItemRepository.save(book);

		Dvd dvd = new Dvd();
		dvd.setTitle("The Matrix");
		dvd.setPublicationYear(1999);
		dvd.setRegionCode("A");
		dvd.setRuntimeMinutes(136);
		libraryItemRepository.save(dvd);

		Magazine magazine = new Magazine();
		magazine.setTitle("National Geographic");
		magazine.setPublicationYear(2024);
		magazine.setIssueNumber("2024-06");
		libraryItemRepository.save(magazine);
	}

	@Test
	void listAllReturnsAllThreeSubtypesPolymorphically() {
		assertThat(libraryCatalogService.listAll()).hasSize(3).extracting(LibraryItemSummary::itemType)
				.containsExactlyInAnyOrder("BOOK", "DVD", "MAGAZINE");
	}

	@Test
	void listBooksReturnsOnlyBooks() {
		assertThat(libraryCatalogService.listBooks()).hasSize(1)
				.allMatch(item -> item.itemType().equals("BOOK"));
	}

	@Test
	void listDvdsReturnsOnlyDvds() {
		assertThat(libraryCatalogService.listDvds()).hasSize(1)
				.allMatch(item -> item.itemType().equals("DVD"));
	}

	@Test
	void listMagazinesReturnsOnlyMagazines() {
		assertThat(libraryCatalogService.listMagazines()).hasSize(1)
				.allMatch(item -> item.itemType().equals("MAGAZINE"));
	}
}
