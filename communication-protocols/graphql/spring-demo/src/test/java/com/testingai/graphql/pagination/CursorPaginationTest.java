package com.testingai.graphql.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorPaginationTest {

	@Test
	void paginate_returnsEmptyConnection_forEmptyList() {
		Connection<String> connection = CursorPagination.paginate(List.of(), null, null);

		assertThat(connection.edges()).isEmpty();
		assertThat(connection.pageInfo().hasNextPage()).isFalse();
		assertThat(connection.pageInfo().endCursor()).isNull();
		assertThat(connection.totalCount()).isZero();
	}

	@Test
	void paginate_returnsFullList_whenFirstExceedsListSize() {
		List<String> items = List.of("a", "b", "c");

		Connection<String> connection = CursorPagination.paginate(items, 10, null);

		assertThat(connection.edges()).extracting(Edge::node).containsExactly("a", "b", "c");
		assertThat(connection.pageInfo().hasNextPage()).isFalse();
		assertThat(connection.totalCount()).isEqualTo(3);
	}

	@Test
	void paginate_stopsExactlyAtPageBoundary_andCursorAdvancesToNextPage() {
		List<String> items = List.of("a", "b", "c", "d");

		Connection<String> firstPage = CursorPagination.paginate(items, 2, null);

		assertThat(firstPage.edges()).extracting(Edge::node).containsExactly("a", "b");
		assertThat(firstPage.pageInfo().hasNextPage()).isTrue();
		assertThat(firstPage.pageInfo().endCursor()).isNotNull();

		Connection<String> secondPage = CursorPagination.paginate(items, 2, firstPage.pageInfo().endCursor());

		assertThat(secondPage.edges()).extracting(Edge::node).containsExactly("c", "d");
		assertThat(secondPage.pageInfo().hasNextPage()).isFalse();
		assertThat(secondPage.totalCount()).isEqualTo(4);
	}

	@Test
	void paginate_returnsEmptyPage_whenAfterPointsPastLastItem() {
		List<String> items = List.of("a", "b");

		Connection<String> lastPage = CursorPagination.paginate(items, 10, null);
		Connection<String> pastEnd = CursorPagination.paginate(items, 10, lastPage.pageInfo().endCursor());

		assertThat(pastEnd.edges()).isEmpty();
		assertThat(pastEnd.pageInfo().hasNextPage()).isFalse();
		assertThat(pastEnd.pageInfo().endCursor()).isNull();
	}

	@Test
	void paginate_throws_whenCursorIsMalformed() {
		assertThatThrownBy(() -> CursorPagination.paginate(List.of("a"), 10, "not-a-valid-cursor!!"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void paginate_defaultsFirstToTen_whenOmitted() {
		List<String> items = IntStream.range(0, 12).mapToObj(String::valueOf).toList();

		Connection<String> connection = CursorPagination.paginate(items, null, null);

		assertThat(connection.edges()).hasSize(10);
		assertThat(connection.pageInfo().hasNextPage()).isTrue();
	}

	@Test
	void paginate_clampsFirstToFiftyMax() {
		List<String> items = IntStream.range(0, 60).mapToObj(String::valueOf).toList();

		Connection<String> connection = CursorPagination.paginate(items, 1000, null);

		assertThat(connection.edges()).hasSize(50);
		assertThat(connection.pageInfo().hasNextPage()).isTrue();
	}

	@Test
	void paginate_throws_whenFirstIsNotPositive() {
		assertThatThrownBy(() -> CursorPagination.paginate(List.of("a"), 0, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
