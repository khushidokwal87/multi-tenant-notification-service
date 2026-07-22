package com.dmg.multi.tenant.notification.service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * tenantName is omitted for platform admins (they have no tenant) and required for tenant
 * admins, since usernames are only unique within a tenant, not globally.
 */
public record LoginRequest(
		@NotBlank String username,
		@NotBlank String password,
		String tenantName) {
}
