package org.openmrs.module.esaudefeatures.web.controller;

import org.openmrs.api.APIAuthenticationException;
import org.openmrs.module.esaudefeatures.web.exception.FhirResourceSearchException;
import org.openmrs.module.esaudefeatures.web.exception.RemoteImportException;
import org.openmrs.module.esaudefeatures.web.exception.RemoteOpenmrsSearchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.net.ssl.SSLException;
import java.net.SocketException;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/25/21.
 */
@ControllerAdvice
public class EsaudeFeaturesResponseExceptionHandler extends ResponseEntityExceptionHandler {
	
	@ExceptionHandler({ FhirResourceSearchException.class })
	public final ResponseEntity<Object> handleOpencrProblems(FhirResourceSearchException ex, WebRequest request) {
		return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.valueOf(ex.getStatusCode()),
		    request);
	}
	
	@ExceptionHandler({ RemoteOpenmrsSearchException.class })
	public final ResponseEntity<Object> handleRemoteOpenmrsExceptions(RemoteOpenmrsSearchException ex, WebRequest request) {
		return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.valueOf(ex.getStatusCode()),
		    request);
	}
	
	@ExceptionHandler({ RemoteImportException.class })
	public final ResponseEntity<Object> handleRemoteImportException(RemoteImportException ex, WebRequest request) {
		return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), ex.getStatus(), request);
	}
	
	@ExceptionHandler({ SSLException.class, SocketException.class })
	public final ResponseEntity<Object> sslExceptions(Exception ex, WebRequest request) {
		return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}
	
	@ExceptionHandler({ APIAuthenticationException.class })
	public final ResponseEntity<Object> authenticationException(APIAuthenticationException ex, WebRequest request) {
		return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
	}
}
