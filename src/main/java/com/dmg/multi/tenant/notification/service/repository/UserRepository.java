package com.dmg.multi.tenant.notification.service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dmg.multi.tenant.notification.service.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByTenantIdAndUsername(Long tenantId, String username);

	Optional<User> findByTenantIdAndEmail(Long tenantId, String email);
}
