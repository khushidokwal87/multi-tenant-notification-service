package com.dmg.multi.tenant.notification.service.entity;

import com.dmg.multi.tenant.notification.service.enums.Channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
	name = "channel_configs",
	uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "channel" })
)
@Getter
@Setter
@NoArgsConstructor
public class ChannelConfig extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Channel channel;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false)
	private int rateLimitPerMinute;

	@Column(nullable = false)
	private int maxRetry;
}
