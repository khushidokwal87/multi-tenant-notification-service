package com.dmg.multi.tenant.notification.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmg.multi.tenant.notification.service.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Optional<Notification> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);
}
