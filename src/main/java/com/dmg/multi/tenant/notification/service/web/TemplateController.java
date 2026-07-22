package com.dmg.multi.tenant.notification.service.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmg.multi.tenant.notification.service.dto.TemplateRequest;
import com.dmg.multi.tenant.notification.service.dto.TemplateResponse;
import com.dmg.multi.tenant.notification.service.entity.Template;
import com.dmg.multi.tenant.notification.service.security.UserPrincipal;
import com.dmg.multi.tenant.notification.service.service.TemplateService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/templates")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TemplateController {

	private final TemplateService templateService;

	public TemplateController(TemplateService templateService) {
		this.templateService = templateService;
	}

	@PostMapping
	public ResponseEntity<TemplateResponse> create(
			@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody TemplateRequest request) {
		Template template = templateService.create(principal.getTenantId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(TemplateResponse.from(template));
	}

	@GetMapping
	public List<TemplateResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
		return templateService.list(principal.getTenantId()).stream().map(TemplateResponse::from).toList();
	}

	@GetMapping("/{id}")
	public TemplateResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
		return TemplateResponse.from(templateService.get(principal.getTenantId(), id));
	}

	@PutMapping("/{id}")
	public TemplateResponse update(
			@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id, @Valid @RequestBody TemplateRequest request) {
		return TemplateResponse.from(templateService.update(principal.getTenantId(), id, request));
	}
}
