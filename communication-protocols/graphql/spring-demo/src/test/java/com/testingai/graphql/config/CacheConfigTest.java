package com.testingai.graphql.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(CacheConfig.class);

	@Test
	void registersBothNamedCachesAsCaffeineBacked() {
		contextRunner.run(context -> {
			CacheManager cacheManager = context.getBean(CacheManager.class);

			Cache categoriesById = cacheManager.getCache(CacheConfig.CATEGORIES_BY_ID);
			Cache categoryChildren = cacheManager.getCache(CacheConfig.CATEGORY_CHILDREN);

			assertThat(categoriesById).isInstanceOf(CaffeineCache.class);
			assertThat(categoryChildren).isInstanceOf(CaffeineCache.class);
		});
	}
}
