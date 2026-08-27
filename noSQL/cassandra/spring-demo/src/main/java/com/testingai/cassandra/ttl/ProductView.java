package com.testingai.cassandra.ttl;

import java.util.UUID;

public record ProductView(UUID productId, UUID viewedAt) {
}
