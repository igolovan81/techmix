package com.testingai.hibernate.associations;

import java.util.List;

public record BookFetchResult(List<BookWithAuthorNames> books, long sqlStatementCount) {
}
