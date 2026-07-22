package com.dmg.multi.tenant.notification.service.dto;

import com.dmg.multi.tenant.notification.service.entity.User;
import com.dmg.multi.tenant.notification.service.enums.Role;

public record UserResponse(Long id, String username, String email, Role role, Long tenantId) {

	public static UserResponse from(User user) {
		Long tenantId = user.getTenant() == null ? null : user.getTenant().getId();
		return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), tenantId);
	}
}
