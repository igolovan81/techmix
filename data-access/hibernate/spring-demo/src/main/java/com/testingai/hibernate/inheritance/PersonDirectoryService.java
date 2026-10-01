package com.testingai.hibernate.inheritance;

import com.testingai.hibernate.entity.Librarian;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.entity.Person;
import com.testingai.hibernate.repository.PersonRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonDirectoryService {

	private final PersonRepository personRepository;

	@Transactional(readOnly = true)
	public List<PersonSummary> listAllPeople() {
		return personRepository.findAll().stream().map(this::toSummary).toList();
	}

	private PersonSummary toSummary(Person person) {
		return switch (person) {
			case Member m -> new PersonSummary(m.getId(), m.getName(), "MEMBER", m.getMembershipDate().toString());
			case Librarian l -> new PersonSummary(l.getId(), l.getName(), "LIBRARIAN", l.getDepartment());
			default -> throw new IllegalStateException("Unknown Person subtype: " + person.getClass());
		};
	}
}
