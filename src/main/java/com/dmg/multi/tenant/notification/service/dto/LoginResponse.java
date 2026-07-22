package com.dmg.multi.tenant.notification.service.dto;

import com.dmg.multi.tenant.notification.service.enums.Role;

public record LoginResponse(
		String token,
		String tokenType,
		String username,
		Role role,
		Long tenantId) {
}
