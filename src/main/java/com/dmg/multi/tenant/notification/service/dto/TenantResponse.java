package com.dmg.multi.tenant.notification.service.dto;

import java.time.Instant;

import com.dmg.multi.tenant.notification.service.entity.Tenant;
import com.dmg.multi.tenant.notification.service.enums.TenantStatus;

public record TenantResponse(Long id, String name, TenantStatus status, Instant createdAt) {

	public static TenantResponse from(Tenant tenant) {
		return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getStatus(), tenant.getCreatedAt());
	}
}
