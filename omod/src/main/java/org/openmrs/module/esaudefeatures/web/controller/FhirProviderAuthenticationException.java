package org.openmrs.module.esaudefeatures.web.controller;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/25/21.
 */
public class FhirProviderAuthenticationException extends Exception {
	
	public FhirProviderAuthenticationException() {
	}
	
	public FhirProviderAuthenticationException(String message) {
		super(message);
	}
}
