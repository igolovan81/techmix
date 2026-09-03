package com.testingai.hibernate.associations;

import java.util.List;

public record ItemFetchResult(List<ItemWithCopyCount> items, long sqlStatementCount) {
}
