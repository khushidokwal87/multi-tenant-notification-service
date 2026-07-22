package com.dmg.multi.tenant.notification.service.service.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.dmg.multi.tenant.notification.service.enums.Channel;

@Component
public class TenantChannelRateLimiter {

	private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

	public boolean tryConsume(Long tenantId, Channel channel, int rateLimitPerMinute) {
		String key = tenantId + ":" + channel;
		TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(rateLimitPerMinute));
		return bucket.tryConsume();
	}
}
