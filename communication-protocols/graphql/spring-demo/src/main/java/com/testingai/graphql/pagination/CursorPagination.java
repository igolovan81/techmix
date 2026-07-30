package com.testingai.graphql.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Relay-style forward-only cursor pagination shared by {@code ProductConnection} and {@code ReviewConnection} — one
 * generic implementation, since GraphQL field resolution goes by property access (edges/node/cursor/pageInfo), not by
 * matching Java class names to GraphQL type names.
 *
 * <p>
 * A cursor encodes a position in the caller's filtered, ordered list (Base64 of {@code "cursor:<index>"}), so a cursor
 * issued under one filter is not guaranteed valid against a different filter — the caller gets whatever position that
 * index decodes to.
 */
public final class CursorPagination {

	private static final String CURSOR_PREFIX = "cursor:";
	private static final int DEFAULT_FIRST = 10;
	private static final int MAX_FIRST = 50;

	private CursorPagination() {
	}

	public static <T> Connection<T> paginate(List<T> items, Integer first, String after) {
		int startIndex = after == null ? 0 : decodeCursor(after) + 1;
		int limit = normalizeFirst(first);
		List<T> page = items.stream().skip(startIndex).limit(limit).toList();
		List<Edge<T>> edges = IntStream.range(0, page.size())
				.mapToObj(i -> new Edge<>(page.get(i), encodeCursor(startIndex + i))).toList();
		boolean hasNextPage = startIndex + page.size() < items.size();
		String endCursor = edges.isEmpty() ? null : edges.getLast().cursor();
		return new Connection<>(edges, new PageInfo(hasNextPage, endCursor), items.size());
	}

	private static int normalizeFirst(Integer first) {
		if (first == null) {
			return DEFAULT_FIRST;
		}
		if (first <= 0) {
			throw new IllegalArgumentException("first must be positive, got " + first);
		}
		return Math.min(first, MAX_FIRST);
	}

	private static String encodeCursor(int index) {
		return Base64.getEncoder().encodeToString((CURSOR_PREFIX + index).getBytes(StandardCharsets.UTF_8));
	}

	private static int decodeCursor(String cursor) {
		String decoded;
		try {
			decoded = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Malformed cursor: " + cursor, e);
		}
		if (!decoded.startsWith(CURSOR_PREFIX)) {
			throw new IllegalArgumentException("Malformed cursor: " + cursor);
		}
		try {
			return Integer.parseInt(decoded.substring(CURSOR_PREFIX.length()));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Malformed cursor: " + cursor, e);
		}
	}
}
