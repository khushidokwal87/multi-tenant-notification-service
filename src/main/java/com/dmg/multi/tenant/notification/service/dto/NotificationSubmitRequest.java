package com.dmg.multi.tenant.notification.service.dto;

import java.time.Instant;
import java.util.Map;

import com.dmg.multi.tenant.notification.service.enums.Channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Either templateId (rendered with variables) or message must be supplied — enforced in
 * NotificationService rather than with bean validation, since it's a cross-field business rule.
 * scheduledTime left null means "send as soon as possible."
 */
public record NotificationSubmitRequest(
		@NotNull Channel channel,
		@NotBlank String recipient,
		Long templateId,
		String subject,
		String message,
		Map<String, String> variables,
		Instant scheduledTime,
		@NotBlank String idempotencyKey) {
}
