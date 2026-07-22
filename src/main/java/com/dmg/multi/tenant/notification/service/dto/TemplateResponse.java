package com.dmg.multi.tenant.notification.service.dto;

import com.dmg.multi.tenant.notification.service.entity.Template;
import com.dmg.multi.tenant.notification.service.enums.Channel;

public record TemplateResponse(
		Long id,
		String name,
		Channel channel,
		String subject,
		String body,
		boolean active,
		Long tenantId) {

	public static TemplateResponse from(Template template) {
		return new TemplateResponse(
				template.getId(),
				template.getName(),
				template.getChannel(),
				template.getSubject(),
				template.getBody(),
				template.isActive(),
				template.getTenant().getId());
	}
}
