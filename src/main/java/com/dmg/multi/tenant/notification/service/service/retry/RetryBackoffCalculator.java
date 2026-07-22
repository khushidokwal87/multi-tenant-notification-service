package com.dmg.multi.tenant.notification.service.service.retry;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RetryBackoffCalculator {

	private final long baseDelaySeconds;
	private final long maxDelaySeconds;

	public RetryBackoffCalculator(
			@Value("${notification.retry.base-delay-seconds:5}") long baseDelaySeconds,
			@Value("${notification.retry.max-delay-seconds:60}") long maxDelaySeconds) {
		this.baseDelaySeconds = baseDelaySeconds;
		this.maxDelaySeconds = maxDelaySeconds;
	}

	public Instant nextRetryTime(int attemptNumber) {
		int shift = Math.min(attemptNumber - 1, 20);
		long delaySeconds = Math.min(maxDelaySeconds, baseDelaySeconds * (1L << shift));
		return Instant.now().plusSeconds(delaySeconds);
	}
}
