package com.dmg.multi.tenant.notification.service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dmg.multi.tenant.notification.service.entity.User;
import com.dmg.multi.tenant.notification.service.enums.Role;
import com.dmg.multi.tenant.notification.service.repository.UserRepository;

/**
 * Seeds exactly one platform admin on first boot so there's a way into the system at all —
 * there's no self-signup flow and every other user is created by a platform admin.
 */
@Component
public class PlatformAdminBootstrapper implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrapper.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String username;
	private final String password;

	public PlatformAdminBootstrapper(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			@Value("${notification.bootstrap.platform-admin.username:platformadmin}") String username,
			@Value("${notification.bootstrap.platform-admin.password:ChangeMe123!}") String password) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.username = username;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.existsByTenantIsNull()) {
			return;
		}
		User platformAdmin = new User();
		platformAdmin.setUsername(username);
		platformAdmin.setEmail(username + "@platform.local");
		platformAdmin.setPassword(passwordEncoder.encode(password));
		platformAdmin.setRole(Role.PLATFORM_ADMIN);
		platformAdmin.setTenant(null);
		userRepository.save(platformAdmin);
		log.warn("Seeded bootstrap platform admin '{}' with the configured default password. "
				+ "Override notification.bootstrap.platform-admin.* before any real deployment.", username);
	}
}
