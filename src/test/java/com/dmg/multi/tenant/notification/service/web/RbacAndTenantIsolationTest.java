package com.dmg.multi.tenant.notification.service.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.dmg.multi.tenant.notification.service.dto.CreateTenantRequest;
import com.dmg.multi.tenant.notification.service.dto.TemplateRequest;
import com.dmg.multi.tenant.notification.service.enums.Channel;

class RbacAndTenantIsolationTest extends AbstractApiIntegrationTest {

	@Test
	void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/templates"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void platformAdminCannotAccessTenantAdminEndpoints() throws Exception {
		String platformAdminToken = loginAsPlatformAdmin();

		mockMvc.perform(get("/api/templates")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + platformAdminToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void tenantAdminCannotAccessPlatformAdminEndpoints() throws Exception {
		String platformAdminToken = loginAsPlatformAdmin();
		String tenantName = "tenant-" + UUID.randomUUID();
		Long tenantId = createTenant(platformAdminToken, tenantName);
		String username = "admin-" + UUID.randomUUID();
		createTenantAdmin(platformAdminToken, tenantId, username, username + "@example.com", "password123");
		String tenantAdminToken = login(username, "password123", tenantName);

		mockMvc.perform(post("/api/tenants")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAdminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateTenantRequest("nope"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void tenantAdminCannotSeeAnotherTenantsTemplate() throws Exception {
		String platformAdminToken = loginAsPlatformAdmin();

		String tenantAName = "tenant-a-" + UUID.randomUUID();
		Long tenantAId = createTenant(platformAdminToken, tenantAName);
		String adminAUsername = "admin-a-" + UUID.randomUUID();
		createTenantAdmin(platformAdminToken, tenantAId, adminAUsername, adminAUsername + "@example.com", "password123");
		String tenantAToken = login(adminAUsername, "password123", tenantAName);

		String tenantBName = "tenant-b-" + UUID.randomUUID();
		Long tenantBId = createTenant(platformAdminToken, tenantBName);
		String adminBUsername = "admin-b-" + UUID.randomUUID();
		createTenantAdmin(platformAdminToken, tenantBId, adminBUsername, adminBUsername + "@example.com", "password123");
		String tenantBToken = login(adminBUsername, "password123", tenantBName);

		TemplateRequest templateRequest = new TemplateRequest("welcome", Channel.EMAIL, "Hi", "Hello {{name}}", true);
		String body = mockMvc.perform(post("/api/templates")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(templateRequest)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long templateId = objectMapper.readTree(body).get("id").asLong();

		mockMvc.perform(get("/api/templates/" + templateId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantBToken))
				.andExpect(status().isNotFound());
	}
}
