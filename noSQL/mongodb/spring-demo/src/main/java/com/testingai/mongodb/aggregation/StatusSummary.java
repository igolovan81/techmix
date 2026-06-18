package com.testingai.mongodb.aggregation;

import org.springframework.data.mongodb.core.mapping.Field;

public record StatusSummary(@Field("_id") String id, long orderCount, double totalRevenue) {
}
