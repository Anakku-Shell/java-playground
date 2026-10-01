package dev.playground.library.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Switches on the cache abstraction: {@code @Cacheable} methods get a proxy that looks in the cache
 * before running the method. With no cache library on the classpath, Spring Boot's
 * {@code CacheManager} keeps each cache in a {@code ConcurrentHashMap}: no expiry and no size limit,
 * fine for a handful of Open Library lookups. Adding Caffeine (local, with expiry) or Redis (shared
 * by every instance) swaps the store without touching the annotations. The cache names are fixed in
 * application.yml. Guide: §5.9 Beyond CRUD.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CachingConfig {}
