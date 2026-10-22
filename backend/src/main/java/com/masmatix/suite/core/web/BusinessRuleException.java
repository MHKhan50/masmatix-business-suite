package com.masmatix.suite.core.web;

/** A request that is well-formed but breaks a business rule (e.g. not enough stock). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) { super(message); }
}
