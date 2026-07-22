package com.dmg.multi.tenant.notification.service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmg.multi.tenant.notification.service.dto.TemplateRequest;
import com.dmg.multi.tenant.notification.service.entity.Tenant;
import com.dmg.multi.tenant.notification.service.entity.Template;
import com.dmg.multi.tenant.notification.service.exception.ConflictException;
import com.dmg.multi.tenant.notification.service.exception.ResourceNotFoundException;
import com.dmg.multi.tenant.notification.service.repository.TemplateRepository;
import com.dmg.multi.tenant.notification.service.repository.TenantRepository;

@Service
public class TemplateService {

	private final TemplateRepository templateRepository;
	private final TenantRepository tenantRepository;

	public TemplateService(TemplateRepository templateRepository, TenantRepository tenantRepository) {
		this.templateRepository = templateRepository;
		this.tenantRepository = tenantRepository;
	}

	@Transactional
	public Template create(Long tenantId, TemplateRequest request) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " not found"));
		if (templateRepository.findByTenantIdAndName(tenantId, request.name()).isPresent()) {
			throw new ConflictException("A template named '" + request.name() + "' already exists for this tenant");
		}
		Template template = new Template();
		template.setTenant(tenant);
		template.setName(request.name());
		template.setChannel(request.channel());
		template.setSubject(request.subject());
		template.setBody(request.body());
		template.setActive(request.active() == null || request.active());
		return templateRepository.save(template);
	}

	public List<Template> list(Long tenantId) {
		return templateRepository.findByTenantId(tenantId);
	}

	public Template get(Long tenantId, Long id) {
		Template template = templateRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Template " + id + " not found"));
		assertOwnedByTenant(template, tenantId);
		return template;
	}

	@Transactional
	public Template update(Long tenantId, Long id, TemplateRequest request) {
		Template template = get(tenantId, id);
		template.setName(request.name());
		template.setChannel(request.channel());
		template.setSubject(request.subject());
		template.setBody(request.body());
		if (request.active() != null) {
			template.setActive(request.active());
		}
		return template;
	}

	private void assertOwnedByTenant(Template template, Long tenantId) {
		if (!template.getTenant().getId().equals(tenantId)) {
			throw new ResourceNotFoundException("Template not found");
		}
	}
}
