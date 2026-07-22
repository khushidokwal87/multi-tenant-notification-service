package com.dmg.multi.tenant.notification.service.dto;

import com.dmg.multi.tenant.notification.service.enums.Channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateRequest(
		@NotBlank String name,
		@NotNull Channel channel,
		String subject,
		@NotBlank String body,
		Boolean active) {
}
