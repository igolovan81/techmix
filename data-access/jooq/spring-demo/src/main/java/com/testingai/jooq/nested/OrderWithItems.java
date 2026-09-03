package com.testingai.jooq.nested;

import java.time.LocalDateTime;
import java.util.List;

public record OrderWithItems(Long id, Long customerId, LocalDateTime placedAt, List<OrderItemView> items) {
}
