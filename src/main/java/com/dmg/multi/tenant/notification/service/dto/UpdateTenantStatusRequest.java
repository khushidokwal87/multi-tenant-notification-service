package com.dmg.multi.tenant.notification.service.dto;

import com.dmg.multi.tenant.notification.service.enums.TenantStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateTenantStatusRequest(@NotNull TenantStatus status) {
}
