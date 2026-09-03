package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
