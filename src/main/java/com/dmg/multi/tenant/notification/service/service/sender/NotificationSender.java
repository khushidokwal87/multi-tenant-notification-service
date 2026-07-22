package com.dmg.multi.tenant.notification.service.service.sender;

import com.dmg.multi.tenant.notification.service.entity.Notification;
import com.dmg.multi.tenant.notification.service.enums.Channel;

public interface NotificationSender {

	Channel getChannel();

	void send(Notification notification) throws TransientSendException;
}
