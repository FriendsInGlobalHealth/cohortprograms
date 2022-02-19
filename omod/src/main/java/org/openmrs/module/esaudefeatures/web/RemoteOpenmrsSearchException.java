package org.openmrs.module.esaudefeatures.web;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/25/21.
 */
public class RemoteOpenmrsSearchException extends RuntimeException {

	private int statusCode;

	public RemoteOpenmrsSearchException(int statusCode) {
		this.statusCode = statusCode;
	}

	public RemoteOpenmrsSearchException(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}
	
	public int getStatusCode() {
		return statusCode;
	}
}
