package com.testingai.graphql.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

/**
 * DB-pushed-down counterpart to {@link CursorPagination}: the caller fetches at most {@code limit + 1} rows already
 * filtered/sorted/limited by the database (typically {@code WHERE id > :cursorId ORDER BY id}), and this class only
 * turns that page into the Relay connection shape — unlike {@link CursorPagination}, it never loads or slices a full
 * result set itself, since avoiding exactly that is the point.
 */
public final class KeysetPagination {

	private static final String CURSOR_PREFIX = "keyset:";
	private static final int DEFAULT_FIRST = 10;
	private static final int MAX_FIRST = 50;

	private KeysetPagination() {
	}

	public static int normalizeFirst(Integer first) {
		if (first == null) {
			return DEFAULT_FIRST;
		}
		if (first <= 0) {
			throw new IllegalArgumentException("first must be positive, got " + first);
		}
		return Math.min(first, MAX_FIRST);
	}

	public static Long decodeCursor(String after) {
		if (after == null) {
			return null;
		}
		String decoded;
		try {
			decoded = new String(Base64.getDecoder().decode(after), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Malformed cursor: " + after, e);
		}
		if (!decoded.startsWith(CURSOR_PREFIX)) {
			throw new IllegalArgumentException("Malformed cursor: " + after);
		}
		try {
			return Long.parseLong(decoded.substring(CURSOR_PREFIX.length()));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Malformed cursor: " + after, e);
		}
	}

	private static String encodeCursor(Long id) {
		return Base64.getEncoder().encodeToString((CURSOR_PREFIX + id).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * {@code rowsLimitPlusOne} must already be sorted by id ascending and contain at most {@code limit + 1} rows — the
	 * extra row, if present, is used only to compute {@code hasNextPage} and is excluded from the page.
	 */
	public static <E, T> Connection<T> paginate(List<E> rowsLimitPlusOne, int limit, Function<E, Long> idOf,
			Function<E, T> mapper, long totalCount) {
		boolean hasNextPage = rowsLimitPlusOne.size() > limit;
		List<E> page = hasNextPage ? rowsLimitPlusOne.subList(0, limit) : rowsLimitPlusOne;
		List<Edge<T>> edges = page.stream().map(row -> new Edge<>(mapper.apply(row), encodeCursor(idOf.apply(row))))
				.toList();
		String endCursor = edges.isEmpty() ? null : edges.getLast().cursor();
		return new Connection<>(edges, new PageInfo(hasNextPage, endCursor), (int) totalCount);
	}
}
