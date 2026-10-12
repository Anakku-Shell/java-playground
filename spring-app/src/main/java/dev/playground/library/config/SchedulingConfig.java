package dev.playground.library.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches on {@code @Scheduled} methods ({@code OverdueLoanJob}). Spring Boot then provides the
 * scheduler: one thread by default ({@code spring.task.scheduling.pool.size}), so a slow job delays
 * the next one. Guide: §5.9 Beyond CRUD.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
