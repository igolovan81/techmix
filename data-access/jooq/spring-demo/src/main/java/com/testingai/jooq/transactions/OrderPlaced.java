package com.testingai.jooq.transactions;

import java.time.LocalDateTime;

public record OrderPlaced(Long orderId, Long customerId, LocalDateTime placedAt) {
}
