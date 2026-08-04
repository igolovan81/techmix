package com.testingai.graphql.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * No category in this schema is ever mutated by a resolver, so there's no {@code @CacheEvict} anywhere in this demo —
 * the 5-minute TTL below is a safety net for out-of-band data changes (e.g. someone editing Postgres directly), not a
 * response to any in-app write path.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	public static final String CATEGORIES_BY_ID = "categoriesById";
	public static final String CATEGORY_CHILDREN = "categoryChildren";

	@Bean
	public CacheManager cacheManager() {
		CaffeineCacheManager manager = new CaffeineCacheManager(CATEGORIES_BY_ID, CATEGORY_CHILDREN);
		manager.setCaffeine(Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(5)));
		return manager;
	}
}
