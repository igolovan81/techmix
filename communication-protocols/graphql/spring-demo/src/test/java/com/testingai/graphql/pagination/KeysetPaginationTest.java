package com.testingai.graphql.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeysetPaginationTest {

	private record Row(Long id, String value) {
	}

	@Test
	void paginate_returnsEmptyConnection_forEmptyList() {
		Connection<String> connection = KeysetPagination.paginate(List.of(), 10, Row::id, Row::value, 0);

		assertThat(connection.edges()).isEmpty();
		assertThat(connection.pageInfo().hasNextPage()).isFalse();
		assertThat(connection.totalCount()).isZero();
	}

	@Test
	void paginate_dropsExtraRow_andSetsHasNextPage_whenMoreRowsThanLimit() {
		List<Row> rowsLimitPlusOne = List.of(new Row(1L, "a"), new Row(2L, "b"), new Row(3L, "c"));

		Connection<String> connection = KeysetPagination.paginate(rowsLimitPlusOne, 2, Row::id, Row::value, 3);

		assertThat(connection.edges()).extracting(Edge::node).containsExactly("a", "b");
		assertThat(connection.pageInfo().hasNextPage()).isTrue();
		assertThat(connection.totalCount()).isEqualTo(3);
	}

	@Test
	void paginate_hasNoNextPage_whenRowsWithinLimit() {
		List<Row> rows = List.of(new Row(1L, "a"), new Row(2L, "b"));

		Connection<String> connection = KeysetPagination.paginate(rows, 10, Row::id, Row::value, 2);

		assertThat(connection.edges()).hasSize(2);
		assertThat(connection.pageInfo().hasNextPage()).isFalse();
	}

	@Test
	void decodeCursor_returnsNull_whenAfterIsNull() {
		assertThat(KeysetPagination.decodeCursor(null)).isNull();
	}

	@Test
	void decodeCursor_roundTrips_throughEncodedCursorFromPaginate() {
		List<Row> rows = List.of(new Row(5L, "a"), new Row(9L, "b"));

		Connection<String> connection = KeysetPagination.paginate(rows, 10, Row::id, Row::value, 2);

		assertThat(KeysetPagination.decodeCursor(connection.pageInfo().endCursor())).isEqualTo(9L);
	}

	@Test
	void decodeCursor_throws_whenCursorIsMalformed() {
		assertThatThrownBy(() -> KeysetPagination.decodeCursor("not-a-valid-cursor!!"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void normalizeFirst_defaultsToTen_whenOmitted() {
		assertThat(KeysetPagination.normalizeFirst(null)).isEqualTo(10);
	}

	@Test
	void normalizeFirst_clampsToFiftyMax() {
		assertThat(KeysetPagination.normalizeFirst(1000)).isEqualTo(50);
	}

	@Test
	void normalizeFirst_throws_whenNotPositive() {
		assertThatThrownBy(() -> KeysetPagination.normalizeFirst(0)).isInstanceOf(IllegalArgumentException.class);
	}
}
