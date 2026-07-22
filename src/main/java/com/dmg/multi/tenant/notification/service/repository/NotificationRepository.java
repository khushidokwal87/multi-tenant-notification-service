package com.dmg.multi.tenant.notification.service.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dmg.multi.tenant.notification.service.entity.Notification;
import com.dmg.multi.tenant.notification.service.enums.NotificationStatus;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Optional<Notification> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

	List<Notification> findByTenantId(Long tenantId);

	@Query("SELECT n.id FROM Notification n WHERE "
			+ "(n.status = :scheduled AND n.scheduledTime <= :now) OR "
			+ "(n.status = :pending AND n.nextRetryAt IS NOT NULL AND n.nextRetryAt <= :now)")
	List<Long> findIdsDueForDispatch(
			@Param("scheduled") NotificationStatus scheduled,
			@Param("pending") NotificationStatus pending,
			@Param("now") Instant now);

	@Modifying
	@Query("UPDATE Notification n SET n.status = :inProgress WHERE n.id IN :ids AND n.status IN :claimableStatuses")
	int claimForDispatch(
			@Param("inProgress") NotificationStatus inProgress,
			@Param("ids") List<Long> ids,
			@Param("claimableStatuses") List<NotificationStatus> claimableStatuses);
}
