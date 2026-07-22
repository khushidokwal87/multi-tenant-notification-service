package com.dmg.multi.tenant.notification.service.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.dmg.multi.tenant.notification.service.enums.Role;

/**
 * tenantId is null for platform admins, who aren't scoped to any single tenant.
 */
public class UserPrincipal implements UserDetails {

	private final Long userId;
	private final Long tenantId;
	private final String username;
	private final String password;
	private final Role role;

	public UserPrincipal(Long userId, Long tenantId, String username, String password, Role role) {
		this.userId = userId;
		this.tenantId = tenantId;
		this.username = username;
		this.password = password;
		this.role = role;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getTenantId() {
		return tenantId;
	}

	public Role getRole() {
		return role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}
}
