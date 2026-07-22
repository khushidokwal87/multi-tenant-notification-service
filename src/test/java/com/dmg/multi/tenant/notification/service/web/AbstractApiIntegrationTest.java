package com.dmg.multi.tenant.notification.service.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.dmg.multi.tenant.notification.service.dto.CreateTenantAdminRequest;
import com.dmg.multi.tenant.notification.service.dto.CreateTenantRequest;
import com.dmg.multi.tenant.notification.service.dto.LoginRequest;
import com.dmg.multi.tenant.notification.service.dto.LoginResponse;
import com.dmg.multi.tenant.notification.service.dto.TenantResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @AutoConfigureMockMvc wires MockMvc through the real Spring Security filter chain (since
 * spring-security-test is on the classpath), so RBAC checks are actually exercised rather than
 * bypassed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractApiIntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ObjectMapper objectMapper;

	protected String login(String username, String password, String tenantName) throws Exception {
		LoginRequest request = new LoginRequest(username, password, tenantName);
		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readValue(body, LoginResponse.class).token();
	}

	protected String loginAsPlatformAdmin() throws Exception {
		return login("platformadmin", "ChangeMe123!", null);
	}

	protected Long createTenant(String platformAdminToken, String tenantName) throws Exception {
		CreateTenantRequest request = new CreateTenantRequest(tenantName);
		String body = mockMvc.perform(post("/api/tenants")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + platformAdminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readValue(body, TenantResponse.class).id();
	}

	protected void createTenantAdmin(
			String platformAdminToken, Long tenantId, String username, String email, String password) throws Exception {
		CreateTenantAdminRequest request = new CreateTenantAdminRequest(username, email, password);
		mockMvc.perform(post("/api/tenants/" + tenantId + "/admins")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + platformAdminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated());
	}
}
