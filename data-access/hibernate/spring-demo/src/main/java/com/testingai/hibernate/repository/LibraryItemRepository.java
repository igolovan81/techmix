package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.LibraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, Long> {
}
