package com.dmg.multi.tenant.notification.service.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dmg.multi.tenant.notification.service.dto.NotificationSubmitRequest;
import com.dmg.multi.tenant.notification.service.entity.Notification;
import com.dmg.multi.tenant.notification.service.entity.NotificationAttempt;
import com.dmg.multi.tenant.notification.service.entity.Tenant;
import com.dmg.multi.tenant.notification.service.entity.Template;
import com.dmg.multi.tenant.notification.service.enums.NotificationStatus;
import com.dmg.multi.tenant.notification.service.exception.BadRequestException;
import com.dmg.multi.tenant.notification.service.exception.ConflictException;
import com.dmg.multi.tenant.notification.service.exception.ResourceNotFoundException;
import com.dmg.multi.tenant.notification.service.repository.NotificationAttemptRepository;
import com.dmg.multi.tenant.notification.service.repository.NotificationRepository;
import com.dmg.multi.tenant.notification.service.repository.TemplateRepository;
import com.dmg.multi.tenant.notification.service.repository.TenantRepository;

/**
 * Immediate sends are handed to the dispatch executor only after this method's transaction
 * commits (via a TransactionSynchronization), so the background thread never races the commit
 * and finds the row missing.
 */
@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationAttemptRepository notificationAttemptRepository;
	private final TemplateRepository templateRepository;
	private final TenantRepository tenantRepository;
	private final TemplateRenderer templateRenderer;
	private final NotificationDispatchService dispatchService;
	private final ExecutorService notificationDispatchExecutor;

	public NotificationService(
			NotificationRepository notificationRepository,
			NotificationAttemptRepository notificationAttemptRepository,
			TemplateRepository templateRepository,
			TenantRepository tenantRepository,
			TemplateRenderer templateRenderer,
			NotificationDispatchService dispatchService,
			ExecutorService notificationDispatchExecutor) {
		this.notificationRepository = notificationRepository;
		this.notificationAttemptRepository = notificationAttemptRepository;
		this.templateRepository = templateRepository;
		this.tenantRepository = tenantRepository;
		this.templateRenderer = templateRenderer;
		this.dispatchService = dispatchService;
		this.notificationDispatchExecutor = notificationDispatchExecutor;
	}

	@Transactional
	public Notification submit(Long tenantId, NotificationSubmitRequest request) {
		Notification existing = notificationRepository
				.findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey())
				.orElse(null);
		if (existing != null) {
			return existing;
		}

		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " not found"));

		String subject = request.subject();
		String message = request.message();
		Template template = null;
		if (request.templateId() != null) {
			template = templateRepository.findById(request.templateId())
					.orElseThrow(() -> new ResourceNotFoundException("Template " + request.templateId() + " not found"));
			if (!template.getTenant().getId().equals(tenantId)) {
				throw new ResourceNotFoundException("Template " + request.templateId() + " not found");
			}
			if (!template.isActive()) {
				throw new BadRequestException("Template '" + template.getName() + "' is not active");
			}
			Map<String, String> variables = request.variables() == null ? Map.of() : request.variables();
			subject = templateRenderer.render(template.getSubject(), variables);
			message = templateRenderer.render(template.getBody(), variables);
		}

		if (message == null || message.isBlank()) {
			throw new BadRequestException("Either templateId or message must be provided");
		}

		Notification notification = new Notification();
		notification.setTenant(tenant);
		notification.setTemplate(template);
		notification.setChannel(request.channel());
		notification.setRecipient(request.recipient());
		notification.setSubject(subject);
		notification.setMessage(message);
		notification.setIdempotencyKey(request.idempotencyKey());
		notification.setAttemptCount(0);
		notification.setScheduledTime(request.scheduledTime());

		boolean dueNow = request.scheduledTime() == null || !request.scheduledTime().isAfter(Instant.now());
		notification.setStatus(dueNow ? NotificationStatus.PENDING : NotificationStatus.SCHEDULED);

		Notification saved;
		try {
			saved = notificationRepository.save(notification);
		} catch (org.springframework.dao.DataIntegrityViolationException ex) {
			// Lost a race against a concurrent submission with the same idempotency key.
			return notificationRepository.findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey())
					.orElseThrow(() -> ex);
		}

		if (dueNow) {
			Long notificationId = saved.getId();
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					notificationDispatchExecutor.submit(() -> dispatchService.dispatch(notificationId));
				}
			});
		}

		return saved;
	}

	public Notification get(Long tenantId, Long id) {
		Notification notification = notificationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notification " + id + " not found"));
		if (!notification.getTenant().getId().equals(tenantId)) {
			throw new ResourceNotFoundException("Notification " + id + " not found");
		}
		return notification;
	}

	public List<Notification> list(Long tenantId) {
		return notificationRepository.findByTenantId(tenantId);
	}

	public List<NotificationAttempt> listAttempts(Long tenantId, Long notificationId) {
		get(tenantId, notificationId);
		return notificationAttemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notificationId);
	}

	@Transactional
	public Notification cancel(Long tenantId, Long id) {
		Notification notification = get(tenantId, id);
		if (notification.getStatus() != NotificationStatus.PENDING && notification.getStatus() != NotificationStatus.SCHEDULED) {
			throw new ConflictException("Cannot cancel a notification in status " + notification.getStatus());
		}
		notification.setStatus(NotificationStatus.CANCELLED);
		notification.setNextRetryAt(null);
		return notification;
	}
}
