package com.dmg.multi.tenant.notification.service.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmg.multi.tenant.notification.service.dto.ChannelConfigRequest;
import com.dmg.multi.tenant.notification.service.dto.ChannelConfigResponse;
import com.dmg.multi.tenant.notification.service.dto.CreateTenantAdminRequest;
import com.dmg.multi.tenant.notification.service.dto.CreateTenantRequest;
import com.dmg.multi.tenant.notification.service.dto.TenantResponse;
import com.dmg.multi.tenant.notification.service.dto.UpdateTenantStatusRequest;
import com.dmg.multi.tenant.notification.service.dto.UserResponse;
import com.dmg.multi.tenant.notification.service.entity.Tenant;
import com.dmg.multi.tenant.notification.service.entity.User;
import com.dmg.multi.tenant.notification.service.enums.Channel;
import com.dmg.multi.tenant.notification.service.service.ChannelConfigService;
import com.dmg.multi.tenant.notification.service.service.TenantService;
import com.dmg.multi.tenant.notification.service.service.UserService;

import jakarta.validation.Valid;

/**
 * Platform-admin only: creating/suspending tenants and their first admin user, plus oversight
 * of any tenant's channel configuration (the "global limits" the PRD gives this role).
 */
@RestController
@RequestMapping("/api/tenants")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TenantController {

	private final TenantService tenantService;
	private final UserService userService;
	private final ChannelConfigService channelConfigService;

	public TenantController(TenantService tenantService, UserService userService, ChannelConfigService channelConfigService) {
		this.tenantService = tenantService;
		this.userService = userService;
		this.channelConfigService = channelConfigService;
	}

	@PostMapping
	public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
		Tenant tenant = tenantService.createTenant(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant));
	}

	@GetMapping
	public List<TenantResponse> list() {
		return tenantService.listTenants().stream().map(TenantResponse::from).toList();
	}

	@GetMapping("/{id}")
	public TenantResponse get(@PathVariable Long id) {
		return TenantResponse.from(tenantService.getTenant(id));
	}

	@PatchMapping("/{id}/status")
	public TenantResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateTenantStatusRequest request) {
		return TenantResponse.from(tenantService.updateStatus(id, request.status()));
	}

	@PostMapping("/{id}/admins")
	public ResponseEntity<UserResponse> createAdmin(@PathVariable Long id, @Valid @RequestBody CreateTenantAdminRequest request) {
		User user = userService.createTenantAdmin(id, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
	}

	@GetMapping("/{id}/channel-configs")
	public List<ChannelConfigResponse> listChannelConfigs(@PathVariable Long id) {
		return channelConfigService.list(id).stream().map(ChannelConfigResponse::from).toList();
	}

	@PutMapping("/{id}/channel-configs/{channel}")
	public ChannelConfigResponse upsertChannelConfig(
			@PathVariable Long id, @PathVariable Channel channel, @Valid @RequestBody ChannelConfigRequest request) {
		return ChannelConfigResponse.from(channelConfigService.upsert(id, channel, request));
	}
}
