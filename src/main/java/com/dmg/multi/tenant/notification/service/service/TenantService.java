package com.dmg.multi.tenant.notification.service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmg.multi.tenant.notification.service.dto.CreateTenantRequest;
import com.dmg.multi.tenant.notification.service.entity.Tenant;
import com.dmg.multi.tenant.notification.service.enums.TenantStatus;
import com.dmg.multi.tenant.notification.service.exception.ConflictException;
import com.dmg.multi.tenant.notification.service.exception.ResourceNotFoundException;
import com.dmg.multi.tenant.notification.service.repository.TenantRepository;

@Service
public class TenantService {

	private final TenantRepository tenantRepository;

	public TenantService(TenantRepository tenantRepository) {
		this.tenantRepository = tenantRepository;
	}

	@Transactional
	public Tenant createTenant(CreateTenantRequest request) {
		if (tenantRepository.findByName(request.name()).isPresent()) {
			throw new ConflictException("A tenant named '" + request.name() + "' already exists");
		}
		Tenant tenant = new Tenant();
		tenant.setName(request.name());
		tenant.setStatus(TenantStatus.ACTIVE);
		return tenantRepository.save(tenant);
	}

	public List<Tenant> listTenants() {
		return tenantRepository.findAll();
	}

	public Tenant getTenant(Long id) {
		return tenantRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant " + id + " not found"));
	}

	@Transactional
	public Tenant updateStatus(Long id, TenantStatus status) {
		Tenant tenant = getTenant(id);
		tenant.setStatus(status);
		return tenant;
	}
}
