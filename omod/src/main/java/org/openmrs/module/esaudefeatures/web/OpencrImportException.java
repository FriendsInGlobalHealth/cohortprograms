package org.openmrs.module.esaudefeatures.web;

import org.springframework.http.HttpStatus;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/23/22.
 */
public class OpencrImportException extends RuntimeException {
	
	private HttpStatus status;
	
	public OpencrImportException(String message, HttpStatus status) {
		super(message);
		this.status = status;
	}
	
	public OpencrImportException(String message, Throwable cause, HttpStatus status) {
		super(message, cause);
		this.status = status;
	}
	
	public HttpStatus getStatus() {
		return status;
	}
}
