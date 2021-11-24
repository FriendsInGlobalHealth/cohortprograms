package org.openmrs.module.esaudefeatures.web.controller;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/25/21.
 */
public class OpencrSearchException extends RuntimeException {
	
	private int statusCode;
	
	public OpencrSearchException(int statusCode) {
		this.statusCode = statusCode;
	}
	
	public OpencrSearchException(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}
	
	public int getStatusCode() {
		return statusCode;
	}
}
