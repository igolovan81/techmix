package com.testingai.hibernate.controller;

import com.testingai.hibernate.associations.BookAuthorService;
import com.testingai.hibernate.associations.BookFetchResult;
import com.testingai.hibernate.associations.CopyBatchFetchService;
import com.testingai.hibernate.associations.ItemFetchResult;
import com.testingai.hibernate.caching.AuthorCacheService;
import com.testingai.hibernate.caching.L1Result;
import com.testingai.hibernate.caching.L2Result;
import com.testingai.hibernate.entity.Address;
import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.entity.Isbn;
import com.testingai.hibernate.entity.LibraryItem;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.inheritance.LibraryCatalogService;
import com.testingai.hibernate.inheritance.LibraryItemSummary;
import com.testingai.hibernate.inheritance.PersonDirectoryService;
import com.testingai.hibernate.inheritance.PersonSummary;
import com.testingai.hibernate.locking.CopyCheckoutService;
import com.testingai.hibernate.repository.AuthorRepository;
import com.testingai.hibernate.repository.BookRepository;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

	private final AuthorRepository authorRepository;
	private final BookRepository bookRepository;
	private final LibraryItemRepository libraryItemRepository;
	private final CopyRepository copyRepository;
	private final MemberRepository memberRepository;
	private final BookAuthorService bookAuthorService;
	private final CopyBatchFetchService copyBatchFetchService;
	private final AuthorCacheService authorCacheService;
	private final CopyCheckoutService copyCheckoutService;
	private final LibraryCatalogService libraryCatalogService;
	private final PersonDirectoryService personDirectoryService;

	public record CreateAuthorRequest(String name) {
	}

	public record CreateBookRequest(String title, int publicationYear, String isbn, List<Long> authorIds) {
	}

	public record CreateCopyRequest(Long libraryItemId, String barcode) {
	}

	public record CreateMemberRequest(
			String name, String email, LocalDate membershipDate, String street, String city, String zipCode) {
	}

	@PostMapping("/authors")
	public Author createAuthor(@RequestBody CreateAuthorRequest request) {
		Author author = new Author();
		author.setName(request.name());
		return authorRepository.save(author);
	}

	@PostMapping("/books")
	public Book createBook(@RequestBody CreateBookRequest request) {
		Book book = new Book();
		book.setTitle(request.title());
		book.setPublicationYear(request.publicationYear());
		if (request.isbn() != null) {
			book.setIsbn(new Isbn(request.isbn()));
		}
		if (request.authorIds() != null && !request.authorIds().isEmpty()) {
			book.setAuthors(Set.copyOf(authorRepository.findAllById(request.authorIds())));
		}
		return bookRepository.save(book);
	}

	@PostMapping("/copies")
	public Copy createCopy(@RequestBody CreateCopyRequest request) {
		LibraryItem item = libraryItemRepository.findById(request.libraryItemId())
				.orElseThrow(() -> new NoSuchElementException("Library item not found: " + request.libraryItemId()));
		Copy copy = new Copy();
		copy.setLibraryItem(item);
		copy.setBarcode(request.barcode());
		copy.setStatus(CopyStatus.AVAILABLE);
		return copyRepository.save(copy);
	}

	@PostMapping("/members")
	public Member createMember(@RequestBody CreateMemberRequest request) {
		Member member = new Member();
		member.setName(request.name());
		member.setEmail(request.email());
		member.setMembershipDate(request.membershipDate());
		member.setAddress(new Address(request.street(), request.city(), request.zipCode()));
		return memberRepository.save(member);
	}

	@GetMapping("/books/n-plus-one")
	public BookFetchResult demonstrateNPlusOne() {
		return bookAuthorService.demonstrateNPlusOne();
	}

	@GetMapping("/books/join-fetch")
	public BookFetchResult findAllWithJoinFetch() {
		return bookAuthorService.findAllWithJoinFetch();
	}

	@GetMapping("/books/entity-graph")
	public BookFetchResult findAllWithEntityGraph() {
		return bookAuthorService.findAllWithEntityGraph();
	}

	@GetMapping("/library-items/batch-fetch")
	public ItemFetchResult demonstrateBatchFetch() {
		return copyBatchFetchService.demonstrateBatchFetch();
	}

	@GetMapping("/authors/{id}/cache/l1")
	public L1Result readTwiceInSameTransaction(@PathVariable Long id) {
		return authorCacheService.readTwiceInSameTransaction(id);
	}

	@GetMapping("/authors/{id}/cache/l2")
	public L2Result readAcrossTwoTransactions(@PathVariable Long id) {
		return authorCacheService.readAcrossTwoTransactions(id);
	}

	@PostMapping("/copies/{copyId}/checkout/optimistic")
	public Loan checkoutOptimistic(@PathVariable Long copyId, @RequestParam Long memberId) {
		return copyCheckoutService.checkoutOptimistic(copyId, memberId);
	}

	@PostMapping("/copies/{copyId}/checkout/pessimistic")
	public Loan checkoutPessimistic(@PathVariable Long copyId, @RequestParam Long memberId) {
		return copyCheckoutService.checkoutPessimistic(copyId, memberId);
	}

	@GetMapping("/library-items")
	public List<LibraryItemSummary> listAllLibraryItems() {
		return libraryCatalogService.listAll();
	}

	@GetMapping("/library-items/books")
	public List<LibraryItemSummary> listBooks() {
		return libraryCatalogService.listBooks();
	}

	@GetMapping("/library-items/dvds")
	public List<LibraryItemSummary> listDvds() {
		return libraryCatalogService.listDvds();
	}

	@GetMapping("/library-items/magazines")
	public List<LibraryItemSummary> listMagazines() {
		return libraryCatalogService.listMagazines();
	}

	@GetMapping("/people")
	public List<PersonSummary> listAllPeople() {
		return personDirectoryService.listAllPeople();
	}

	@ExceptionHandler(com.testingai.hibernate.locking.CopyAlreadyLoanedException.class)
	public ResponseEntity<String> handleCopyAlreadyLoaned(com.testingai.hibernate.locking.CopyAlreadyLoanedException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}
}
