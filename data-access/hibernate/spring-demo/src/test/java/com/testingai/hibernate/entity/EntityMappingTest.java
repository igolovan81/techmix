package com.testingai.hibernate.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.repository.AuthorRepository;
import com.testingai.hibernate.repository.BookRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.LoanRepository;
import com.testingai.hibernate.repository.MemberRepository;
import com.testingai.hibernate.repository.PersonRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class EntityMappingTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private LoanRepository loanRepository;

	@Test
	void singleTableInheritancePersistsAndReloadsCorrectSubtypes() {
		Author author = new Author();
		author.setName("Martin Fowler");
		authorRepository.save(author);

		Book book = new Book();
		book.setTitle("Refactoring");
		book.setPublicationYear(1999);
		book.setIsbn(new Isbn("978-0134757599"));
		book.setAuthors(Set.of(author));
		bookRepository.save(book);

		Dvd dvd = new Dvd();
		dvd.setTitle("The Matrix");
		dvd.setPublicationYear(1999);
		dvd.setRegionCode("A");
		dvd.setRuntimeMinutes(136);
		libraryItemRepository.save(dvd);

		entityManager.flush();
		entityManager.clear();

		List<LibraryItem> items = libraryItemRepository.findAll();
		assertThat(items).hasSize(2);
		assertThat(items).anyMatch(Book.class::isInstance).anyMatch(Dvd.class::isInstance);

		Book reloadedBook = bookRepository.findAll().get(0);
		assertThat(reloadedBook.getIsbn()).isEqualTo(new Isbn("978-0134757599"));
		assertThat(reloadedBook.getAuthors()).extracting(Author::getName).containsExactly("Martin Fowler");
	}

	@Test
	void joinedInheritancePersistsAndReloadsCorrectSubtypes() {
		Member member = new Member();
		member.setName("Ada Lovelace");
		member.setEmail("ada@example.com");
		member.setMembershipDate(LocalDate.of(2020, 1, 1));
		member.setAddress(new Address("1 Analytical Engine Way", "London", "SW1A 1AA"));
		memberRepository.save(member);

		Librarian librarian = new Librarian();
		librarian.setName("Marian the Librarian");
		librarian.setEmail("marian@example.com");
		librarian.setEmployeeId("EMP-001");
		librarian.setDepartment("Reference");
		personRepository.save(librarian);

		entityManager.flush();
		entityManager.clear();

		List<Person> people = personRepository.findAll();
		assertThat(people).hasSize(2);
		assertThat(people).anyMatch(Member.class::isInstance).anyMatch(Librarian.class::isInstance);

		Member reloadedMember = memberRepository.findAll().get(0);
		assertThat(reloadedMember.getAddress().getCity()).isEqualTo("London");
	}

	@Test
	void loanAuditingStampsCreatedAndUpdatedTimestampsOnSave() {
		Member member = new Member();
		member.setName("Grace Hopper");
		member.setEmail("grace@example.com");
		member.setMembershipDate(LocalDate.of(2021, 1, 1));
		memberRepository.save(member);

		Book book = new Book();
		book.setTitle("The Pragmatic Programmer");
		book.setPublicationYear(1999);
		libraryItemRepository.save(book);

		Copy copy = new Copy();
		copy.setLibraryItem(book);
		copy.setBarcode("BC-1");
		copy.setStatus(CopyStatus.AVAILABLE);
		entityManager.persist(copy);

		Loan loan = new Loan();
		loan.setCopy(copy);
		loan.setMember(member);
		loan.setLoanDate(LocalDate.now());
		loan.setDueDate(LocalDate.now().plusWeeks(2));
		loanRepository.save(loan);

		entityManager.flush();
		entityManager.clear();

		Loan reloaded = loanRepository.findAll().get(0);
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}
}
