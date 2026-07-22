package com.dmg.multi.tenant.notification.service.service.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dmg.multi.tenant.notification.service.enums.Channel;

class TenantChannelRateLimiterTest {

	private final TenantChannelRateLimiter rateLimiter = new TenantChannelRateLimiter();

	@Test
	void tracksBucketsIndependentlyPerTenantAndChannel() {
		assertThat(rateLimiter.tryConsume(1L, Channel.EMAIL, 1)).isTrue();
		assertThat(rateLimiter.tryConsume(1L, Channel.EMAIL, 1)).isFalse();

		// Different tenant, same channel: independent bucket, still has capacity.
		assertThat(rateLimiter.tryConsume(2L, Channel.EMAIL, 1)).isTrue();

		// Same tenant, different channel: independent bucket, still has capacity.
		assertThat(rateLimiter.tryConsume(1L, Channel.SMS, 1)).isTrue();
	}
}
