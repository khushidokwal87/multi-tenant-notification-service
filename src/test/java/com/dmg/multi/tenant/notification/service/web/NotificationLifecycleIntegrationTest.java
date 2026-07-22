package com.dmg.multi.tenant.notification.service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.dmg.multi.tenant.notification.service.dto.ChannelConfigRequest;
import com.dmg.multi.tenant.notification.service.dto.NotificationSubmitRequest;
import com.dmg.multi.tenant.notification.service.dto.TemplateRequest;
import com.dmg.multi.tenant.notification.service.enums.Channel;
import tools.jackson.databind.JsonNode;

/**
 * failure-rate=0 makes simulated sends deterministic so the eventual SENT status can be
 * asserted without flakiness.
 */
@TestPropertySource(properties = "notification.simulation.failure-rate=0")
class NotificationLifecycleIntegrationTest extends AbstractApiIntegrationTest {

	private String setUpTenantAdmin() throws Exception {
		String platformAdminToken = loginAsPlatformAdmin();
		String tenantName = "tenant-" + UUID.randomUUID();
		Long tenantId = createTenant(platformAdminToken, tenantName);
		String username = "admin-" + UUID.randomUUID();
		createTenantAdmin(platformAdminToken, tenantId, username, username + "@example.com", "password123");
		return login(username, "password123", tenantName);
	}

	@Test
	void submittedNotificationIsRenderedAndEventuallySent() throws Exception {
		String token = setUpTenantAdmin();

		mockMvc.perform(put("/api/channel-configs/EMAIL")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ChannelConfigRequest(true, 100, 3))))
				.andExpect(status().isOk());

		TemplateRequest templateRequest = new TemplateRequest("welcome", Channel.EMAIL, "Hi {{name}}", "Hello {{name}}, welcome!", true);
		String templateBody = mockMvc.perform(post("/api/templates")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(templateRequest)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long templateId = objectMapper.readTree(templateBody).get("id").asLong();

		NotificationSubmitRequest submitRequest = new NotificationSubmitRequest(
				Channel.EMAIL, "user@example.com", templateId, null, null,
				Map.of("name", "Ada"), null, "idem-" + UUID.randomUUID());
		String submitBody = mockMvc.perform(post("/api/notifications")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(submitRequest)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode submitted = objectMapper.readTree(submitBody);
		assertThat(submitted.get("subject").asText()).isEqualTo("Hi Ada");
		assertThat(submitted.get("message").asText()).isEqualTo("Hello Ada, welcome!");
		Long notificationId = submitted.get("id").asLong();

		String finalStatus = waitForTerminalStatus(token, notificationId);
		assertThat(finalStatus).isEqualTo("SENT");
	}

	@Test
	void resubmittingSameIdempotencyKeyReturnsTheOriginalNotification() throws Exception {
		String token = setUpTenantAdmin();
		mockMvc.perform(put("/api/channel-configs/EMAIL")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ChannelConfigRequest(true, 100, 3))))
				.andExpect(status().isOk());

		String idempotencyKey = "idem-" + UUID.randomUUID();
		NotificationSubmitRequest request = new NotificationSubmitRequest(
				Channel.EMAIL, "user@example.com", null, "Subject", "Body", null, null, idempotencyKey);

		String firstBody = mockMvc.perform(post("/api/notifications")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long firstId = objectMapper.readTree(firstBody).get("id").asLong();

		String secondBody = mockMvc.perform(post("/api/notifications")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long secondId = objectMapper.readTree(secondBody).get("id").asLong();

		assertThat(secondId).isEqualTo(firstId);
	}

	@Test
	void scheduledNotificationCanBeCancelledBeforeItsDue() throws Exception {
		String token = setUpTenantAdmin();

		NotificationSubmitRequest request = new NotificationSubmitRequest(
				Channel.EMAIL, "user@example.com", null, "Subject", "Body", null,
				Instant.now().plusSeconds(3600), "idem-" + UUID.randomUUID());
		String submitBody = mockMvc.perform(post("/api/notifications")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode submitted = objectMapper.readTree(submitBody);
		assertThat(submitted.get("status").asText()).isEqualTo("SCHEDULED");
		Long notificationId = submitted.get("id").asLong();

		mockMvc.perform(post("/api/notifications/" + notificationId + "/cancel")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));
	}

	private String waitForTerminalStatus(String token, Long notificationId) throws Exception {
		String status = "PENDING";
		for (int i = 0; i < 25; i++) {
			String body = mockMvc.perform(get("/api/notifications/" + notificationId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andReturn().getResponse().getContentAsString();
			status = objectMapper.readTree(body).get("status").asText();
			if ("SENT".equals(status) || "FAILED".equals(status)) {
				return status;
			}
			Thread.sleep(200);
		}
		return status;
	}
}
