package com.dmg.multi.tenant.notification.service.dto;

import java.time.Instant;

import com.dmg.multi.tenant.notification.service.entity.NotificationAttempt;
import com.dmg.multi.tenant.notification.service.enums.AttemptStatus;

public record NotificationAttemptResponse(
		Long id,
		int attemptNumber,
		AttemptStatus status,
		String errorMessage,
		Instant attemptedAt) {

	public static NotificationAttemptResponse from(NotificationAttempt attempt) {
		return new NotificationAttemptResponse(
				attempt.getId(),
				attempt.getAttemptNumber(),
				attempt.getStatus(),
				attempt.getErrorMessage(),
				attempt.getCreatedAt());
	}
}
