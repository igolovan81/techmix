package com.testingai.cassandra.consistency;

import com.testingai.cassandra.crud.Product;

public record ConsistencyReadResult(Product product, String consistencyLevel, long elapsedMillis) {
}
