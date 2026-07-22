package com.dmg.multi.tenant.notification.service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmg.multi.tenant.notification.service.dto.ChannelConfigRequest;
import com.dmg.multi.tenant.notification.service.entity.ChannelConfig;
import com.dmg.multi.tenant.notification.service.entity.Tenant;
import com.dmg.multi.tenant.notification.service.enums.Channel;
import com.dmg.multi.tenant.notification.service.exception.ResourceNotFoundException;
import com.dmg.multi.tenant.notification.service.repository.ChannelConfigRepository;
import com.dmg.multi.tenant.notification.service.repository.TenantRepository;

@Service
public class ChannelConfigService {

	private final ChannelConfigRepository channelConfigRepository;
	private final TenantRepository tenantRepository;

	public ChannelConfigService(ChannelConfigRepository channelConfigRepository, TenantRepository tenantRepository) {
		this.channelConfigRepository = channelConfigRepository;
		this.tenantRepository = tenantRepository;
	}

	@Transactional
	public ChannelConfig upsert(Long tenantId, Channel channel, ChannelConfigRequest request) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " not found"));
		ChannelConfig config = channelConfigRepository.findByTenantIdAndChannel(tenantId, channel)
				.orElseGet(() -> {
					ChannelConfig created = new ChannelConfig();
					created.setTenant(tenant);
					created.setChannel(channel);
					return created;
				});
		config.setEnabled(request.enabled());
		config.setRateLimitPerMinute(request.rateLimitPerMinute());
		config.setMaxRetry(request.maxRetry());
		return channelConfigRepository.save(config);
	}

	public List<ChannelConfig> list(Long tenantId) {
		return channelConfigRepository.findByTenantId(tenantId);
	}

	public ChannelConfig get(Long tenantId, Channel channel) {
		return channelConfigRepository.findByTenantIdAndChannel(tenantId, channel)
				.orElseThrow(() -> new ResourceNotFoundException("No configuration for channel " + channel));
	}
}
