package com.dmg.multi.tenant.notification.service.service.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenBucketTest {

	@Test
	void allowsConsumptionUpToCapacityThenDenies() {
		TokenBucket bucket = new TokenBucket(3);

		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isFalse();
	}

	@Test
	void zeroCapacityAlwaysDenies() {
		TokenBucket bucket = new TokenBucket(0);

		assertThat(bucket.tryConsume()).isFalse();
	}
}
