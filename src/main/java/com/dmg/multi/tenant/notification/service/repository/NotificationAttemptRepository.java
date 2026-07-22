package com.dmg.multi.tenant.notification.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmg.multi.tenant.notification.service.entity.NotificationAttempt;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {

	List<NotificationAttempt> findByNotificationIdOrderByAttemptNumberAsc(Long notificationId);
}
