package org.openmrs.module.esaudefeatures.web.exception;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/25/21.
 */
public class FhirResourceSearchException extends RuntimeException {
	
	private int statusCode;
	
	public FhirResourceSearchException(int statusCode) {
		this.statusCode = statusCode;
	}
	
	public FhirResourceSearchException(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}
	
	public int getStatusCode() {
		return statusCode;
	}
}
