package com.dmg.multi.tenant.notification.service.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DispatchExecutorConfig {

	@Bean(destroyMethod = "shutdown")
	public ExecutorService notificationDispatchExecutor(
			@Value("${notification.dispatch.core-pool-size:4}") int corePoolSize,
			@Value("${notification.dispatch.max-pool-size:8}") int maxPoolSize,
			@Value("${notification.dispatch.queue-capacity:200}") int queueCapacity) {
		AtomicInteger threadCount = new AtomicInteger(1);
		ThreadFactory threadFactory = runnable -> {
			Thread thread = new Thread(runnable, "notification-dispatch-" + threadCount.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		};
		return new ThreadPoolExecutor(
				corePoolSize,
				maxPoolSize,
				60L, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(queueCapacity),
				threadFactory,
				new ThreadPoolExecutor.CallerRunsPolicy());
	}
}
