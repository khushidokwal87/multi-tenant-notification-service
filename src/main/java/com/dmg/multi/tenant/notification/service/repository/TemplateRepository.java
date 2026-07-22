package com.dmg.multi.tenant.notification.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmg.multi.tenant.notification.service.entity.Template;

public interface TemplateRepository extends JpaRepository<Template, Long> {

	Optional<Template> findByTenantIdAndName(Long tenantId, String name);
}
