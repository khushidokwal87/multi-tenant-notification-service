package com.dmg.multi.tenant.notification.service.dto;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		Map<String, String> fieldErrors) {

	public ApiErrorResponse(int status, String error, String message) {
		this(Instant.now(), status, error, message, null);
	}

	public ApiErrorResponse(int status, String error, String message, Map<String, String> fieldErrors) {
		this(Instant.now(), status, error, message, fieldErrors);
	}
}
