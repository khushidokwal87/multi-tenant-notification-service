package com.dmg.multi.tenant.notification.service.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dmg.multi.tenant.notification.service.service.NotificationDispatchService;

@Component
public class NotificationDispatchScheduler {

	private final NotificationDispatchService dispatchService;
	private final ExecutorService notificationDispatchExecutor;

	public NotificationDispatchScheduler(
			NotificationDispatchService dispatchService,
			ExecutorService notificationDispatchExecutor) {
		this.dispatchService = dispatchService;
		this.notificationDispatchExecutor = notificationDispatchExecutor;
	}

	@Scheduled(fixedDelayString = "${notification.dispatch.poll-interval-ms:2000}")
	public void pollDueNotifications() {
		List<Long> dueIds = dispatchService.claimDueForDispatch(Instant.now());
		dueIds.forEach(id -> notificationDispatchExecutor.submit(() -> dispatchService.dispatch(id)));
	}
}
