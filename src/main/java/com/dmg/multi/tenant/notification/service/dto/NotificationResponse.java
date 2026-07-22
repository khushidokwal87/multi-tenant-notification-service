package com.dmg.multi.tenant.notification.service.dto;

import java.time.Instant;

import com.dmg.multi.tenant.notification.service.entity.Notification;
import com.dmg.multi.tenant.notification.service.enums.Channel;
import com.dmg.multi.tenant.notification.service.enums.NotificationStatus;

public record NotificationResponse(
		Long id,
		Channel channel,
		String recipient,
		String subject,
		String message,
		NotificationStatus status,
		Instant scheduledTime,
		Instant sentTime,
		int attemptCount,
		Instant nextRetryAt,
		Long templateId,
		Long tenantId) {

	public static NotificationResponse from(Notification notification) {
		Long templateId = notification.getTemplate() == null ? null : notification.getTemplate().getId();
		return new NotificationResponse(
				notification.getId(),
				notification.getChannel(),
				notification.getRecipient(),
				notification.getSubject(),
				notification.getMessage(),
				notification.getStatus(),
				notification.getScheduledTime(),
				notification.getSentTime(),
				notification.getAttemptCount(),
				notification.getNextRetryAt(),
				templateId,
				notification.getTenant().getId());
	}
}
