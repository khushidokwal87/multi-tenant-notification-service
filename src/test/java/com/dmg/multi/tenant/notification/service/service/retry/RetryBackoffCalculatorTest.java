package com.dmg.multi.tenant.notification.service.service.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RetryBackoffCalculatorTest {

	private final RetryBackoffCalculator calculator = new RetryBackoffCalculator(5, 60);

	@Test
	void delayDoublesWithEachAttempt() {
		Instant now = Instant.now();

		Instant firstRetry = calculator.nextRetryTime(1);
		Instant secondRetry = calculator.nextRetryTime(2);
		Instant thirdRetry = calculator.nextRetryTime(3);

		assertThat(firstRetry).isBetween(now.plusSeconds(4), now.plusSeconds(6));
		assertThat(secondRetry).isBetween(now.plusSeconds(9), now.plusSeconds(11));
		assertThat(thirdRetry).isBetween(now.plusSeconds(19), now.plusSeconds(21));
	}

	@Test
	void delayIsCappedAtMaxDelay() {
		Instant now = Instant.now();

		Instant lateRetry = calculator.nextRetryTime(10);

		assertThat(lateRetry).isBetween(now.plusSeconds(59), now.plusSeconds(61));
	}
}
