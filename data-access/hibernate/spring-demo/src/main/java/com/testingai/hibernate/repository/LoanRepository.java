package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {
}
