package com.bhargavang.caredesk.common;

/** A request that is well-formed but breaks a rule, e.g. booking outside working hours. */
public class BusinessRuleException extends RuntimeException {

	public BusinessRuleException(String message) {
		super(message);
	}
}
