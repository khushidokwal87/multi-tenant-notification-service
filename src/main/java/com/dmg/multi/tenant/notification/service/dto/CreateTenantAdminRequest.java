package com.dmg.multi.tenant.notification.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantAdminRequest(
		@NotBlank String username,
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8) String password) {
}
