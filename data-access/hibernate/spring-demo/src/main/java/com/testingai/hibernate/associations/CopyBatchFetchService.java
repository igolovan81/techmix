package com.testingai.hibernate.associations;

import com.testingai.hibernate.entity.LibraryItem;
import com.testingai.hibernate.repository.LibraryItemRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CopyBatchFetchService {

	private final LibraryItemRepository libraryItemRepository;
	private final EntityManager entityManager;

	@Transactional(readOnly = true)
	public ItemFetchResult demonstrateBatchFetch() {
		statistics().clear();
		List<LibraryItem> items = libraryItemRepository.findAll();
		List<ItemWithCopyCount> views = items.stream()
				.map(item -> new ItemWithCopyCount(item.getId(), item.getTitle(), item.getCopies().size()))
				.toList();
		return new ItemFetchResult(views, statistics().getPrepareStatementCount());
	}

	private Statistics statistics() {
		return entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
	}
}
