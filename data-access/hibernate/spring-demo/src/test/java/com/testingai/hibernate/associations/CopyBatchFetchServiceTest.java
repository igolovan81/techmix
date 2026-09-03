package com.testingai.hibernate.associations;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(CopyBatchFetchService.class)
class CopyBatchFetchServiceTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private CopyRepository copyRepository;

	@Autowired
	private CopyBatchFetchService copyBatchFetchService;

	@BeforeEach
	void seedFifteenItemsEachWithOneCopy() {
		for (int i = 0; i < 15; i++) {
			Book book = new Book();
			book.setTitle("Book " + i);
			book.setPublicationYear(2000 + i);
			libraryItemRepository.save(book);

			Copy copy = new Copy();
			copy.setLibraryItem(book);
			copy.setBarcode("BC-" + i);
			copy.setStatus(CopyStatus.AVAILABLE);
			copyRepository.save(copy);
		}
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void batchFetchGroupsCollectionLoadsIntoBatchesOfTen() {
		ItemFetchResult result = copyBatchFetchService.demonstrateBatchFetch();

		assertThat(result.items()).hasSize(15);
		assertThat(result.items()).allMatch(item -> item.copyCount() == 1);
		// 1 select for findAll() + 2 batched selects for 15 items' copies (batch size 10: one batch of 10, one of 5)
		assertThat(result.sqlStatementCount()).isEqualTo(3);
	}
}
