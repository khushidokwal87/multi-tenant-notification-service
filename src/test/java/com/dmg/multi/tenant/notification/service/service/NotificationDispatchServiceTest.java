package com.dmg.multi.tenant.notification.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dmg.multi.tenant.notification.service.entity.ChannelConfig;
import com.dmg.multi.tenant.notification.service.entity.Notification;
import com.dmg.multi.tenant.notification.service.entity.NotificationAttempt;
import com.dmg.multi.tenant.notification.service.entity.Tenant;
import com.dmg.multi.tenant.notification.service.enums.AttemptStatus;
import com.dmg.multi.tenant.notification.service.enums.Channel;
import com.dmg.multi.tenant.notification.service.enums.NotificationStatus;
import com.dmg.multi.tenant.notification.service.repository.ChannelConfigRepository;
import com.dmg.multi.tenant.notification.service.repository.NotificationAttemptRepository;
import com.dmg.multi.tenant.notification.service.repository.NotificationRepository;
import com.dmg.multi.tenant.notification.service.service.ratelimit.TenantChannelRateLimiter;
import com.dmg.multi.tenant.notification.service.service.retry.RetryBackoffCalculator;
import com.dmg.multi.tenant.notification.service.service.sender.NotificationSender;
import com.dmg.multi.tenant.notification.service.service.sender.NotificationSenderRegistry;
import com.dmg.multi.tenant.notification.service.service.sender.TransientSendException;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

	private static final Long TENANT_ID = 1L;
	private static final Long NOTIFICATION_ID = 100L;

	@Mock
	private NotificationRepository notificationRepository;
	@Mock
	private NotificationAttemptRepository notificationAttemptRepository;
	@Mock
	private ChannelConfigRepository channelConfigRepository;
	@Mock
	private NotificationSenderRegistry senderRegistry;
	@Mock
	private TenantChannelRateLimiter rateLimiter;
	@Mock
	private RetryBackoffCalculator backoffCalculator;
	@Mock
	private NotificationSender sender;

	private NotificationDispatchService dispatchService;
	private Notification notification;
	private ChannelConfig channelConfig;

	@BeforeEach
	void setUp() {
		dispatchService = new NotificationDispatchService(
				notificationRepository, notificationAttemptRepository, channelConfigRepository,
				senderRegistry, rateLimiter, backoffCalculator);

		Tenant tenant = new Tenant();
		tenant.setId(TENANT_ID);

		notification = new Notification();
		notification.setId(NOTIFICATION_ID);
		notification.setTenant(tenant);
		notification.setChannel(Channel.EMAIL);
		notification.setRecipient("user@example.com");
		notification.setMessage("hello");
		notification.setStatus(NotificationStatus.PENDING);
		notification.setAttemptCount(0);

		channelConfig = new ChannelConfig();
		channelConfig.setTenant(tenant);
		channelConfig.setChannel(Channel.EMAIL);
		channelConfig.setEnabled(true);
		channelConfig.setRateLimitPerMinute(100);
		channelConfig.setMaxRetry(3);

		lenient().when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));
		lenient().when(channelConfigRepository.findByTenantIdAndChannel(TENANT_ID, Channel.EMAIL))
				.thenReturn(Optional.of(channelConfig));
		lenient().when(rateLimiter.tryConsume(TENANT_ID, Channel.EMAIL, 100)).thenReturn(true);
		lenient().when(senderRegistry.getSender(Channel.EMAIL)).thenReturn(sender);
	}

	@Test
	void successfulSendMarksNotificationSentAndRecordsAttempt() throws TransientSendException {
		doNothing().when(sender).send(notification);

		dispatchService.dispatch(NOTIFICATION_ID);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getSentTime()).isNotNull();
		assertThat(notification.getNextRetryAt()).isNull();

		ArgumentCaptor<NotificationAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationAttempt.class);
		verify(notificationAttemptRepository).save(attemptCaptor.capture());
		assertThat(attemptCaptor.getValue().getStatus()).isEqualTo(AttemptStatus.SUCCESS);
		assertThat(attemptCaptor.getValue().getAttemptNumber()).isEqualTo(1);
	}

	@Test
	void transientFailureWithRetriesRemainingSchedulesNextAttempt() throws TransientSendException {
		Instant nextRetry = Instant.now().plusSeconds(5);
		doThrow(new TransientSendException("boom")).when(sender).send(notification);
		when(backoffCalculator.nextRetryTime(1)).thenReturn(nextRetry);

		dispatchService.dispatch(NOTIFICATION_ID);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getNextRetryAt()).isEqualTo(nextRetry);

		ArgumentCaptor<NotificationAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationAttempt.class);
		verify(notificationAttemptRepository).save(attemptCaptor.capture());
		assertThat(attemptCaptor.getValue().getStatus()).isEqualTo(AttemptStatus.FAILED);
	}

	@Test
	void transientFailureExceedingMaxRetryMarksTerminallyFailed() throws TransientSendException {
		notification.setAttemptCount(2);
		doThrow(new TransientSendException("boom")).when(sender).send(notification);

		dispatchService.dispatch(NOTIFICATION_ID);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(notification.getAttemptCount()).isEqualTo(3);
		assertThat(notification.getNextRetryAt()).isNull();
		verify(backoffCalculator, never()).nextRetryTime(anyInt());
	}

	@Test
	void disabledChannelFailsTerminallyWithoutSending() {
		channelConfig.setEnabled(false);

		dispatchService.dispatch(NOTIFICATION_ID);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
		verify(senderRegistry, never()).getSender(any());
		verify(notificationAttemptRepository, times(1)).save(any());
	}

	@Test
	void rateLimitedNotificationIsReQueuedWithoutCountingAsAnAttempt() {
		when(rateLimiter.tryConsume(TENANT_ID, Channel.EMAIL, 100)).thenReturn(false);

		dispatchService.dispatch(NOTIFICATION_ID);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getAttemptCount()).isEqualTo(0);
		assertThat(notification.getNextRetryAt()).isNotNull();
		verify(senderRegistry, never()).getSender(any());
		verify(notificationAttemptRepository, never()).save(any());
	}

	@Test
	void alreadyTerminalNotificationIsSkipped() {
		notification.setStatus(NotificationStatus.SENT);

		dispatchService.dispatch(NOTIFICATION_ID);

		verify(channelConfigRepository, never()).findByTenantIdAndChannel(any(), any());
		verify(senderRegistry, never()).getSender(any());
	}
}
