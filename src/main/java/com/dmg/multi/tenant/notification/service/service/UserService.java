package com.dmg.multi.tenant.notification.service.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dmg.multi.tenant.notification.service.dto.CreateTenantAdminRequest;
import com.dmg.multi.tenant.notification.service.entity.Tenant;
import com.dmg.multi.tenant.notification.service.entity.User;
import com.dmg.multi.tenant.notification.service.enums.Role;
import com.dmg.multi.tenant.notification.service.exception.ConflictException;
import com.dmg.multi.tenant.notification.service.exception.ResourceNotFoundException;
import com.dmg.multi.tenant.notification.service.repository.TenantRepository;
import com.dmg.multi.tenant.notification.service.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, TenantRepository tenantRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User createTenantAdmin(Long tenantId, CreateTenantAdminRequest request) {
		Tenant tenant = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResourceNotFoundException("Tenant " + tenantId + " not found"));
		if (userRepository.findByTenantIdAndUsername(tenantId, request.username()).isPresent()) {
			throw new ConflictException("Username '" + request.username() + "' is already taken for this tenant");
		}
		if (userRepository.findByTenantIdAndEmail(tenantId, request.email()).isPresent()) {
			throw new ConflictException("Email '" + request.email() + "' is already taken for this tenant");
		}
		User user = new User();
		user.setTenant(tenant);
		user.setUsername(request.username());
		user.setEmail(request.email());
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setRole(Role.TENANT_ADMIN);
		return userRepository.save(user);
	}
}
