package com.dmg.multi.tenant.notification.service.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmg.multi.tenant.notification.service.dto.NotificationAttemptResponse;
import com.dmg.multi.tenant.notification.service.dto.NotificationResponse;
import com.dmg.multi.tenant.notification.service.dto.NotificationSubmitRequest;
import com.dmg.multi.tenant.notification.service.entity.Notification;
import com.dmg.multi.tenant.notification.service.security.UserPrincipal;
import com.dmg.multi.tenant.notification.service.service.NotificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@PostMapping
	public ResponseEntity<NotificationResponse> submit(
			@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody NotificationSubmitRequest request) {
		Notification notification = notificationService.submit(principal.getTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(NotificationResponse.from(notification));
	}

	@GetMapping
	public List<NotificationResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
		return notificationService.list(principal.getTenantId()).stream().map(NotificationResponse::from).toList();
	}

	@GetMapping("/{id}")
	public NotificationResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
		return NotificationResponse.from(notificationService.get(principal.getTenantId(), id));
	}

	@GetMapping("/{id}/attempts")
	public List<NotificationAttemptResponse> attempts(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
		return notificationService.listAttempts(principal.getTenantId(), id).stream()
				.map(NotificationAttemptResponse::from)
				.toList();
	}

	@PostMapping("/{id}/cancel")
	public NotificationResponse cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
		return NotificationResponse.from(notificationService.cancel(principal.getTenantId(), id));
	}
}
