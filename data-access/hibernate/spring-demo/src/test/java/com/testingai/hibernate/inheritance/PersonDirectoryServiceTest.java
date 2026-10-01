package com.testingai.hibernate.inheritance;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Librarian;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.repository.PersonRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(PersonDirectoryService.class)
class PersonDirectoryServiceTest {

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private PersonDirectoryService personDirectoryService;

	@BeforeEach
	void seedOneMemberAndOneLibrarian() {
		Member member = new Member();
		member.setName("Ada Lovelace");
		member.setEmail("ada@example.com");
		member.setMembershipDate(LocalDate.of(2020, 1, 1));
		personRepository.save(member);

		Librarian librarian = new Librarian();
		librarian.setName("Marian the Librarian");
		librarian.setEmail("marian@example.com");
		librarian.setEmployeeId("EMP-001");
		librarian.setDepartment("Reference");
		personRepository.save(librarian);
	}

	@Test
	void listAllPeopleReturnsBothSubtypesPolymorphicallyAcrossTheJoinedTables() {
		assertThat(personDirectoryService.listAllPeople()).hasSize(2)
				.extracting(PersonSummary::personType)
				.containsExactlyInAnyOrder("MEMBER", "LIBRARIAN");
	}
}
