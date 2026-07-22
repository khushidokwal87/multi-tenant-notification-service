package com.dmg.multi.tenant.notification.service.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Substitutes {@code {{variableName}}} placeholders in template text. A placeholder with no
 * matching entry in the variables map is replaced with an empty string rather than failing,
 * so a tenant's template changes don't break on an unused variable.
 */
@Component
public class TemplateRenderer {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

	public String render(String text, Map<String, String> variables) {
		if (text == null) {
			return null;
		}
		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String value = variables == null ? null : variables.get(matcher.group(1));
			matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
