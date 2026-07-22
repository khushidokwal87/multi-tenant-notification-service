package com.dmg.multi.tenant.notification.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChannelConfigRequest(
		boolean enabled,
		@PositiveOrZero int rateLimitPerMinute,
		@PositiveOrZero @NotNull Integer maxRetry) {
}
