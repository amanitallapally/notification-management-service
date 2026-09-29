package com.schwab.assessment.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * In-process async delivery pipeline configuration.
 *
 * Trade-off (documented in memory-bank/decisions.md): a real production
 * system would use a durable broker (SQS/Kafka/RabbitMQ) so queued
 * deliveries survive a process restart and can scale across instances. This
 * prototype uses an in-JVM thread pool so the whole system runs with zero
 * external infrastructure. Because state transitions are still persisted to
 * the DB per attempt, a restart loses only in-flight (already-claimed but
 * not yet completed) attempts, which the retry scheduler will not
 * automatically resume in this prototype (documented limitation).
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "deliveryExecutor")
    public Executor deliveryExecutor(
            @Value("${notification.delivery.worker-pool-size:4}") int poolSize,
            @Value("${notification.delivery.queue-capacity:1000}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("delivery-worker-");
        executor.initialize();
        return executor;
    }
}
