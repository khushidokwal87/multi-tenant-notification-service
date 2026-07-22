package com.dmg.multi.tenant.notification.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dmg.multi.tenant.notification.service.enums.Channel;
import com.dmg.multi.tenant.notification.service.service.sender.NotificationSender;
import com.dmg.multi.tenant.notification.service.service.sender.SimulatedNotificationSender;

@Configuration
public class NotificationSenderConfig {

	@Bean
	public NotificationSender emailNotificationSender(
			@Value("${notification.simulation.failure-rate:0.3}") double failureRate) {
		return new SimulatedNotificationSender(Channel.EMAIL, failureRate);
	}

	@Bean
	public NotificationSender smsNotificationSender(
			@Value("${notification.simulation.failure-rate:0.3}") double failureRate) {
		return new SimulatedNotificationSender(Channel.SMS, failureRate);
	}

	@Bean
	public NotificationSender pushNotificationSender(
			@Value("${notification.simulation.failure-rate:0.3}") double failureRate) {
		return new SimulatedNotificationSender(Channel.PUSH, failureRate);
	}

	@Bean
	public NotificationSender inAppNotificationSender(
			@Value("${notification.simulation.failure-rate:0.3}") double failureRate) {
		return new SimulatedNotificationSender(Channel.IN_APP, failureRate);
	}
}
