package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Copy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopyRepository extends JpaRepository<Copy, Long> {
}
