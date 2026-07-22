package com.dmg.multi.tenant.notification.service.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.dmg.multi.tenant.notification.service.dto.LoginRequest;
import com.dmg.multi.tenant.notification.service.dto.LoginResponse;
import com.dmg.multi.tenant.notification.service.entity.Tenant;
import com.dmg.multi.tenant.notification.service.entity.User;
import com.dmg.multi.tenant.notification.service.repository.TenantRepository;
import com.dmg.multi.tenant.notification.service.repository.UserRepository;
import com.dmg.multi.tenant.notification.service.security.JwtService;
import com.dmg.multi.tenant.notification.service.security.UserPrincipal;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(
			UserRepository userRepository,
			TenantRepository tenantRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService) {
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public LoginResponse login(LoginRequest request) {
		User user = resolveUser(request);
		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new BadCredentialsException("Invalid username or password");
		}

		Long tenantId = user.getTenant() == null ? null : user.getTenant().getId();
		UserPrincipal principal = new UserPrincipal(user.getId(), tenantId, user.getUsername(), user.getPassword(), user.getRole());
		String token = jwtService.generateToken(principal);
		return new LoginResponse(token, "Bearer", user.getUsername(), user.getRole(), tenantId);
	}

	private User resolveUser(LoginRequest request) {
		if (request.tenantName() == null || request.tenantName().isBlank()) {
			return userRepository.findByUsernameAndTenantIsNull(request.username())
					.orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
		}
		Tenant tenant = tenantRepository.findByName(request.tenantName())
				.orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
		return userRepository.findByTenantIdAndUsername(tenant.getId(), request.username())
				.orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
	}
}
