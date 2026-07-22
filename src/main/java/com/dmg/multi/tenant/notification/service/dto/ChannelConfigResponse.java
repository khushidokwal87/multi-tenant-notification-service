package com.dmg.multi.tenant.notification.service.dto;

import com.dmg.multi.tenant.notification.service.entity.ChannelConfig;
import com.dmg.multi.tenant.notification.service.enums.Channel;

public record ChannelConfigResponse(
		Long id,
		Channel channel,
		boolean enabled,
		int rateLimitPerMinute,
		int maxRetry,
		Long tenantId) {

	public static ChannelConfigResponse from(ChannelConfig config) {
		return new ChannelConfigResponse(
				config.getId(),
				config.getChannel(),
				config.isEnabled(),
				config.getRateLimitPerMinute(),
				config.getMaxRetry(),
				config.getTenant().getId());
	}
}
