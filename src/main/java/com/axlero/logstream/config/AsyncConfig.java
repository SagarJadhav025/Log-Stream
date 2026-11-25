package com.axlero.logstream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Day 3: Java 21 Virtual Threads & Executor Pool Configuration.
 * Replaces platform thread pools with virtual thread executors for
 * maximum concurrency without thread starvation.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Primary async executor backed by Java 21 Virtual Threads.
     * Virtual threads are lightweight (not OS threads) — spawn millions without exhaustion.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Dedicated Spring @Async executor for alerting & webhook dispatchers.
     * Uses virtual threads to avoid blocking the main event loop.
     */
    @Bean(name = "alertExecutor")
    public Executor alertExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Fixed-size bounded executor for time-series histogram queries
     * to prevent unbounded resource use on expensive analytical queries.
     */
    @Bean(name = "queryExecutor")
    public ThreadPoolTaskExecutor queryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("logstream-query-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
