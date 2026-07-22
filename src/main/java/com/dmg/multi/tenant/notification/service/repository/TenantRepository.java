package com.dmg.multi.tenant.notification.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmg.multi.tenant.notification.service.entity.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

	Optional<Tenant> findByName(String name);
}
