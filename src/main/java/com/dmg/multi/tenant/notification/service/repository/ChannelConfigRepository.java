package com.dmg.multi.tenant.notification.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmg.multi.tenant.notification.service.entity.ChannelConfig;
import com.dmg.multi.tenant.notification.service.enums.Channel;

public interface ChannelConfigRepository extends JpaRepository<ChannelConfig, Long> {

	Optional<ChannelConfig> findByTenantIdAndChannel(Long tenantId, Channel channel);

	List<ChannelConfig> findByTenantId(Long tenantId);
}
