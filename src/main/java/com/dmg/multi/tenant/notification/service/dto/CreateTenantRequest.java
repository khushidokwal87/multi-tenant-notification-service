package com.dmg.multi.tenant.notification.service.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(@NotBlank String name) {
}
