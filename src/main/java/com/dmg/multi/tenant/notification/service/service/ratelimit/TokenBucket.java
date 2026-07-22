package com.dmg.multi.tenant.notification.service.service.ratelimit;

public class TokenBucket {

	private final int capacity;
	private final double refillTokensPerMillis;
	private double availableTokens;
	private long lastRefillMillis;

	public TokenBucket(int capacityPerMinute) {
		this.capacity = Math.max(capacityPerMinute, 0);
		this.refillTokensPerMillis = this.capacity / 60_000.0;
		this.availableTokens = this.capacity;
		this.lastRefillMillis = System.currentTimeMillis();
	}

	public synchronized boolean tryConsume() {
		refill();
		if (availableTokens >= 1.0) {
			availableTokens -= 1.0;
			return true;
		}
		return false;
	}

	private void refill() {
		long now = System.currentTimeMillis();
		long elapsedMillis = now - lastRefillMillis;
		if (elapsedMillis <= 0) {
			return;
		}
		availableTokens = Math.min(capacity, availableTokens + elapsedMillis * refillTokensPerMillis);
		lastRefillMillis = now;
	}
}
