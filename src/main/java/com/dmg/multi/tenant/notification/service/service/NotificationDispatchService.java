package com.dmg.multi.tenant.notification.service.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmg.multi.tenant.notification.service.entity.ChannelConfig;
import com.dmg.multi.tenant.notification.service.entity.Notification;
import com.dmg.multi.tenant.notification.service.entity.NotificationAttempt;
import com.dmg.multi.tenant.notification.service.enums.AttemptStatus;
import com.dmg.multi.tenant.notification.service.enums.NotificationStatus;
import com.dmg.multi.tenant.notification.service.repository.ChannelConfigRepository;
import com.dmg.multi.tenant.notification.service.repository.NotificationAttemptRepository;
import com.dmg.multi.tenant.notification.service.repository.NotificationRepository;
import com.dmg.multi.tenant.notification.service.service.ratelimit.TenantChannelRateLimiter;
import com.dmg.multi.tenant.notification.service.service.retry.RetryBackoffCalculator;
import com.dmg.multi.tenant.notification.service.service.sender.NotificationSender;
import com.dmg.multi.tenant.notification.service.service.sender.NotificationSenderRegistry;
import com.dmg.multi.tenant.notification.service.service.sender.TransientSendException;

/**
 * Claiming and dispatching are split into separate {@code @Transactional} methods so that callers
 * (e.g. the scheduler) invoke them through the Spring proxy rather than via self-invocation, which
 * would silently skip transaction handling.
 */
@Service
public class NotificationDispatchService {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

	private final NotificationRepository notificationRepository;
	private final NotificationAttemptRepository notificationAttemptRepository;
	private final ChannelConfigRepository channelConfigRepository;
	private final NotificationSenderRegistry senderRegistry;
	private final TenantChannelRateLimiter rateLimiter;
	private final RetryBackoffCalculator backoffCalculator;

	public NotificationDispatchService(
			NotificationRepository notificationRepository,
			NotificationAttemptRepository notificationAttemptRepository,
			ChannelConfigRepository channelConfigRepository,
			NotificationSenderRegistry senderRegistry,
			TenantChannelRateLimiter rateLimiter,
			RetryBackoffCalculator backoffCalculator) {
		this.notificationRepository = notificationRepository;
		this.notificationAttemptRepository = notificationAttemptRepository;
		this.channelConfigRepository = channelConfigRepository;
		this.senderRegistry = senderRegistry;
		this.rateLimiter = rateLimiter;
		this.backoffCalculator = backoffCalculator;
	}

	@Transactional
	public List<Long> claimDueForDispatch(Instant now) {
		List<Long> dueIds = notificationRepository.findIdsDueForDispatch(
				NotificationStatus.SCHEDULED, NotificationStatus.PENDING, now);
		if (!dueIds.isEmpty()) {
			notificationRepository.claimForDispatch(
					NotificationStatus.IN_PROGRESS, dueIds,
					List.of(NotificationStatus.SCHEDULED, NotificationStatus.PENDING));
		}
		return dueIds;
	}

	@Transactional
	public void dispatch(Long notificationId) {
		Notification notification = notificationRepository.findById(notificationId).orElse(null);
		if (notification == null) {
			log.warn("Notification {} not found, skipping dispatch", notificationId);
			return;
		}
		if (isTerminal(notification.getStatus())) {
			return;
		}

		ChannelConfig channelConfig = channelConfigRepository
				.findByTenantIdAndChannel(notification.getTenant().getId(), notification.getChannel())
				.orElse(null);

		if (channelConfig == null || !channelConfig.isEnabled()) {
			failTerminally(notification,
					"Channel " + notification.getChannel() + " is not configured or disabled for this tenant");
			return;
		}

		if (!rateLimiter.tryConsume(
				notification.getTenant().getId(), notification.getChannel(), channelConfig.getRateLimitPerMinute())) {
			notification.setStatus(NotificationStatus.PENDING);
			notification.setNextRetryAt(Instant.now().plusSeconds(1));
			return;
		}

		int attemptNumber = notification.getAttemptCount() + 1;
		NotificationSender sender = senderRegistry.getSender(notification.getChannel());
		try {
			sender.send(notification);
			notification.setAttemptCount(attemptNumber);
			notification.setStatus(NotificationStatus.SENT);
			notification.setSentTime(Instant.now());
			notification.setNextRetryAt(null);
			recordAttempt(notification, attemptNumber, AttemptStatus.SUCCESS, null);
		} catch (TransientSendException ex) {
			notification.setAttemptCount(attemptNumber);
			recordAttempt(notification, attemptNumber, AttemptStatus.FAILED, ex.getMessage());
			if (attemptNumber >= channelConfig.getMaxRetry()) {
				notification.setStatus(NotificationStatus.FAILED);
				notification.setNextRetryAt(null);
			} else {
				notification.setStatus(NotificationStatus.PENDING);
				notification.setNextRetryAt(backoffCalculator.nextRetryTime(attemptNumber));
			}
		}
	}

	private void failTerminally(Notification notification, String reason) {
		int attemptNumber = notification.getAttemptCount() + 1;
		notification.setAttemptCount(attemptNumber);
		notification.setStatus(NotificationStatus.FAILED);
		notification.setNextRetryAt(null);
		recordAttempt(notification, attemptNumber, AttemptStatus.FAILED, reason);
	}

	private void recordAttempt(Notification notification, int attemptNumber, AttemptStatus status, String errorMessage) {
		NotificationAttempt attempt = new NotificationAttempt();
		attempt.setNotification(notification);
		attempt.setAttemptNumber(attemptNumber);
		attempt.setStatus(status);
		attempt.setErrorMessage(errorMessage);
		notificationAttemptRepository.save(attempt);
	}

	private boolean isTerminal(NotificationStatus status) {
		return status == NotificationStatus.SENT
				|| status == NotificationStatus.FAILED
				|| status == NotificationStatus.CANCELLED;
	}
}
