package com.dmg.multi.tenant.notification.service.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtService {

	private final SecretKey key;
	private final long expirationMillis;

	public JwtService(
			@Value("${notification.security.jwt.secret}") String secret,
			@Value("${notification.security.jwt.expiration-minutes:60}") long expirationMinutes) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMillis = expirationMinutes * 60_000;
	}

	public String generateToken(UserPrincipal principal) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(principal.getUserId()))
				.claim("username", principal.getUsername())
				.claim("role", principal.getRole().name())
				.claim("tenantId", principal.getTenantId())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(expirationMillis)))
				.signWith(key)
				.compact();
	}

	public Claims parseClaims(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}
}
