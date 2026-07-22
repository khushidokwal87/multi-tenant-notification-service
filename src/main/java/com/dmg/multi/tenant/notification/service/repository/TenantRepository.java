package com.dmg.multi.tenant.notification.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmg.multi.tenant.notification.service.entity.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
}
