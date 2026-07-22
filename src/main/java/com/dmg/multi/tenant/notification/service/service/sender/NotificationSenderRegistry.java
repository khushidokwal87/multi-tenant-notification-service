package com.dmg.multi.tenant.notification.service.service.sender;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.dmg.multi.tenant.notification.service.enums.Channel;

@Component
public class NotificationSenderRegistry {

	private final Map<Channel, NotificationSender> sendersByChannel;

	public NotificationSenderRegistry(List<NotificationSender> senders) {
		this.sendersByChannel = senders.stream()
				.collect(Collectors.toMap(NotificationSender::getChannel, Function.identity()));
	}

	public NotificationSender getSender(Channel channel) {
		NotificationSender sender = sendersByChannel.get(channel);
		if (sender == null) {
			throw new IllegalStateException("No sender registered for channel " + channel);
		}
		return sender;
	}
}
