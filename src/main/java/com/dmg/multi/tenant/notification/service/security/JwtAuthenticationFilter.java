package com.dmg.multi.tenant.notification.service.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dmg.multi.tenant.notification.service.enums.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The JWT is self-issued and signed, so its claims are trusted directly without a database
 * lookup on every request — this keeps auth stateless and off the hot path.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith("Bearer ")) {
			try {
				Claims claims = jwtService.parseClaims(header.substring(7));
				Long userId = Long.valueOf(claims.getSubject());
				String username = claims.get("username", String.class);
				Role role = Role.valueOf(claims.get("role", String.class));
				Object tenantClaim = claims.get("tenantId");
				Long tenantId = tenantClaim == null ? null : Long.valueOf(tenantClaim.toString());

				UserPrincipal principal = new UserPrincipal(userId, tenantId, username, "", role);
				UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (JwtException | IllegalArgumentException ex) {
				SecurityContextHolder.clearContext();
			}
		}
		chain.doFilter(request, response);
	}
}
