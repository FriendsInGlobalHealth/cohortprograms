package org.openmrs.module.esaudefeatures.web.exception;

import org.springframework.http.HttpStatus;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/18/22.
 */
public class RemoteImportException extends RuntimeException {
	
	private HttpStatus status;
	
	public RemoteImportException(String message, HttpStatus status) {
		super(message);
		this.status = status;
	}
	
	public RemoteImportException(String message, Throwable cause, HttpStatus status) {
		super(message, cause);
		this.status = status;
	}
	
	public HttpStatus getStatus() {
		return status;
	}
}
