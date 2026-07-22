package com.dmg.multi.tenant.notification.service.service.sender;

public class TransientSendException extends Exception {

	public TransientSendException(String message) {
		super(message);
	}
}
