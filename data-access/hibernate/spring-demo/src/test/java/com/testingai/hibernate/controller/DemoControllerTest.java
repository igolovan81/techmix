package com.testingai.hibernate.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.hibernate.associations.BookAuthorService;
import com.testingai.hibernate.associations.BookFetchResult;
import com.testingai.hibernate.associations.CopyBatchFetchService;
import com.testingai.hibernate.associations.ItemFetchResult;
import com.testingai.hibernate.caching.AuthorCacheService;
import com.testingai.hibernate.caching.L1Result;
import com.testingai.hibernate.caching.L2Result;
import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.inheritance.LibraryCatalogService;
import com.testingai.hibernate.inheritance.PersonDirectoryService;
import com.testingai.hibernate.locking.CopyCheckoutService;
import com.testingai.hibernate.repository.AuthorRepository;
import com.testingai.hibernate.repository.BookRepository;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthorRepository authorRepository;

	@MockitoBean
	private BookRepository bookRepository;

	@MockitoBean
	private LibraryItemRepository libraryItemRepository;

	@MockitoBean
	private CopyRepository copyRepository;

	@MockitoBean
	private MemberRepository memberRepository;

	@MockitoBean
	private BookAuthorService bookAuthorService;

	@MockitoBean
	private CopyBatchFetchService copyBatchFetchService;

	@MockitoBean
	private AuthorCacheService authorCacheService;

	@MockitoBean
	private CopyCheckoutService copyCheckoutService;

	@MockitoBean
	private LibraryCatalogService libraryCatalogService;

	@MockitoBean
	private PersonDirectoryService personDirectoryService;

	@Test
	void createAuthorReturns200() throws Exception {
		Author author = new Author();
		author.setId(1L);
		author.setName("Ada Lovelace");
		when(authorRepository.save(any())).thenReturn(author);

		mockMvc.perform(post("/demo/authors")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new DemoController.CreateAuthorRequest("Ada Lovelace"))))
				.andExpect(status().isOk());
	}

	@Test
	void demonstrateNPlusOneReturns200() throws Exception {
		when(bookAuthorService.demonstrateNPlusOne()).thenReturn(new BookFetchResult(List.of(), 0));

		mockMvc.perform(get("/demo/books/n-plus-one")).andExpect(status().isOk());
	}

	@Test
	void demonstrateBatchFetchReturns200() throws Exception {
		when(copyBatchFetchService.demonstrateBatchFetch()).thenReturn(new ItemFetchResult(List.of(), 0));

		mockMvc.perform(get("/demo/library-items/batch-fetch")).andExpect(status().isOk());
	}

	@Test
	void readTwiceInSameTransactionReturns200() throws Exception {
		when(authorCacheService.readTwiceInSameTransaction(1L)).thenReturn(new L1Result("a", "a", 1));

		mockMvc.perform(get("/demo/authors/1/cache/l1")).andExpect(status().isOk());
	}

	@Test
	void readAcrossTwoTransactionsReturns200() throws Exception {
		when(authorCacheService.readAcrossTwoTransactions(1L)).thenReturn(new L2Result(1, 0, 1, 1));

		mockMvc.perform(get("/demo/authors/1/cache/l2")).andExpect(status().isOk());
	}

	@Test
	void checkoutOptimisticReturns200() throws Exception {
		Loan loan = new Loan();
		loan.setId(1L);
		when(copyCheckoutService.checkoutOptimistic(1L, 1L)).thenReturn(loan);

		mockMvc.perform(post("/demo/copies/1/checkout/optimistic?memberId=1"))
				.andExpect(status().isOk());
	}

	@Test
	void checkoutPessimisticReturns200() throws Exception {
		Loan loan = new Loan();
		loan.setId(1L);
		when(copyCheckoutService.checkoutPessimistic(1L, 1L)).thenReturn(loan);

		mockMvc.perform(post("/demo/copies/1/checkout/pessimistic?memberId=1"))
				.andExpect(status().isOk());
	}

	@Test
	void listAllLibraryItemsReturns200() throws Exception {
		when(libraryCatalogService.listAll()).thenReturn(List.of());

		mockMvc.perform(get("/demo/library-items")).andExpect(status().isOk());
	}

	@Test
	void listAllPeopleReturns200() throws Exception {
		when(personDirectoryService.listAllPeople()).thenReturn(List.of());

		mockMvc.perform(get("/demo/people")).andExpect(status().isOk());
	}
}
