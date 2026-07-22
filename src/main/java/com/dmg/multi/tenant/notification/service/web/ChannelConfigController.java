package com.dmg.multi.tenant.notification.service.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dmg.multi.tenant.notification.service.dto.ChannelConfigRequest;
import com.dmg.multi.tenant.notification.service.dto.ChannelConfigResponse;
import com.dmg.multi.tenant.notification.service.enums.Channel;
import com.dmg.multi.tenant.notification.service.security.UserPrincipal;
import com.dmg.multi.tenant.notification.service.service.ChannelConfigService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/channel-configs")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class ChannelConfigController {

	private final ChannelConfigService channelConfigService;

	public ChannelConfigController(ChannelConfigService channelConfigService) {
		this.channelConfigService = channelConfigService;
	}

	@GetMapping
	public List<ChannelConfigResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
		return channelConfigService.list(principal.getTenantId()).stream().map(ChannelConfigResponse::from).toList();
	}

	@GetMapping("/{channel}")
	public ChannelConfigResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Channel channel) {
		return ChannelConfigResponse.from(channelConfigService.get(principal.getTenantId(), channel));
	}

	@PutMapping("/{channel}")
	public ChannelConfigResponse upsert(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Channel channel,
			@Valid @RequestBody ChannelConfigRequest request) {
		return ChannelConfigResponse.from(channelConfigService.upsert(principal.getTenantId(), channel, request));
	}
}
