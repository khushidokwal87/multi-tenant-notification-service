package com.dmg.multi.tenant.notification.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Excludes UserDetailsServiceAutoConfiguration: auth here is a manual username/password lookup
 * against our own User table plus self-issued JWTs, not Spring Security's UserDetailsService /
 * AuthenticationManager flow, so the default in-memory user Boot would otherwise generate is
 * unused noise.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableJpaAuditing
@EnableScheduling
public class MultiTenantNotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MultiTenantNotificationServiceApplication.class, args);
	}

}
