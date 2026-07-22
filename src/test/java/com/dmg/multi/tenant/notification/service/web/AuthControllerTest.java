package com.dmg.multi.tenant.notification.service.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.dmg.multi.tenant.notification.service.dto.LoginRequest;

class AuthControllerTest extends AbstractApiIntegrationTest {

	@Test
	void bootstrapPlatformAdminCanLogIn() throws Exception {
		LoginRequest request = new LoginRequest("platformadmin", "ChangeMe123!", null);

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("PLATFORM_ADMIN"))
				.andExpect(jsonPath("$.tenantId").doesNotExist())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		LoginRequest request = new LoginRequest("platformadmin", "definitely-wrong", null);

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unknownTenantIsRejected() throws Exception {
		LoginRequest request = new LoginRequest("someone", "whatever123", "no-such-tenant");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized());
	}
}
