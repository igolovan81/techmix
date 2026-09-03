package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
