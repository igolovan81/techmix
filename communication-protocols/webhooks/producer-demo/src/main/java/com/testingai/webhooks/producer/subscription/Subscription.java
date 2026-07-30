package com.testingai.webhooks.producer.subscription;

import java.util.Set;

public record Subscription(String id, String callbackUrl, String secret, Set<String> eventTypes) {
}
