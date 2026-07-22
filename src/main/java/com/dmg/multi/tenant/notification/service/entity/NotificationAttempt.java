package com.dmg.multi.tenant.notification.service.entity;

import com.dmg.multi.tenant.notification.service.enums.AttemptStatus;

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
	name = "notification_attempts",
	uniqueConstraints = @UniqueConstraint(columnNames = { "notification_id", "attempt_number" })
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationAttempt extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "notification_id", nullable = false)
	private Notification notification;

	@Column(nullable = false)
	private int attemptNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AttemptStatus status;

	@Column(columnDefinition = "TEXT")
	private String errorMessage;
}
