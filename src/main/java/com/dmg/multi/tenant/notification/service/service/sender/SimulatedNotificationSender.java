package com.dmg.multi.tenant.notification.service.service.sender;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dmg.multi.tenant.notification.service.entity.Notification;
import com.dmg.multi.tenant.notification.service.enums.Channel;

public class SimulatedNotificationSender implements NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(SimulatedNotificationSender.class);

	private final Channel channel;
	private final double failureRate;

	public SimulatedNotificationSender(Channel channel, double failureRate) {
		this.channel = channel;
		this.failureRate = failureRate;
	}

	@Override
	public Channel getChannel() {
		return channel;
	}

	@Override
	public void send(Notification notification) throws TransientSendException {
		log.info("Dispatching {} notification {} to {}", channel, notification.getId(), notification.getRecipient());
		if (ThreadLocalRandom.current().nextDouble() < failureRate) {
			throw new TransientSendException(
					"Simulated transient failure delivering " + channel + " notification " + notification.getId());
		}
		log.info("Delivered {} notification {} to {}", channel, notification.getId(), notification.getRecipient());
	}
}
