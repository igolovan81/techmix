package com.testingai.hibernate.inheritance;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Dvd;
import com.testingai.hibernate.entity.LibraryItem;
import com.testingai.hibernate.entity.Magazine;
import com.testingai.hibernate.repository.LibraryItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryCatalogService {

	private final LibraryItemRepository libraryItemRepository;

	@Transactional(readOnly = true)
	public List<LibraryItemSummary> listAll() {
		return libraryItemRepository.findAll().stream().map(this::toSummary).toList();
	}

	@Transactional(readOnly = true)
	public List<LibraryItemSummary> listBooks() {
		return libraryItemRepository.findAll().stream()
				.filter(Book.class::isInstance)
				.map(this::toSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<LibraryItemSummary> listDvds() {
		return libraryItemRepository.findAll().stream()
				.filter(Dvd.class::isInstance)
				.map(this::toSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<LibraryItemSummary> listMagazines() {
		return libraryItemRepository.findAll().stream()
				.filter(Magazine.class::isInstance)
				.map(this::toSummary)
				.toList();
	}

	private LibraryItemSummary toSummary(LibraryItem item) {
		String itemType = switch (item) {
			case Book b -> "BOOK";
			case Dvd d -> "DVD";
			case Magazine m -> "MAGAZINE";
			default -> throw new IllegalStateException("Unknown LibraryItem subtype: " + item.getClass());
		};
		return new LibraryItemSummary(item.getId(), item.getTitle(), item.getPublicationYear(), itemType);
	}
}
